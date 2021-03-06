'''
Module to test the run_msqc_pipeline.
'''

from QC import run_msqc_pipeline, parse_metrics
from pkg_resources import resource_filename  # @UnresolvedImport
from time import gmtime, strftime
from shutil import copy
import os.path
import shutil
import tempfile
import time
import unittest

__author__ = "Marcel Kempenaar"
__contact__ = "brs@nbic.nl"
__copyright__ = "Copyright, 2012, Netherlands Bioinformatics Centre"
__license__ = "MIT"


class Test(unittest.TestCase):
    '''
    Test functions for the run_msqc_pipeline code in the QC module
    '''
    def setUp(self):
        # Log files
        self.logfile = resource_filename(__name__, "data/qc_logfile.txt")
        self.robocopylog = resource_filename(__name__, "data/robocopylog.txt")

        # Data files
        self.rawfile = resource_filename(__name__, "data/ltq_ctmm_test_data.RAW")
        self.rawfilebase = os.path.split(os.path.splitext(self.rawfile)[0])[1]
        self.mzxmlfile = resource_filename(__name__, "data/ltq_ctmm_test_data.RAW.mzXML")
        self.mgffile = resource_filename(__name__, "data/ltq_ctmm_test_data.RAW.MGF")

        # Create a temp working dir for each test
        self.temp_folder = tempfile.mkdtemp(prefix='test_run_nist_')

    def test_logfiles(self):
        ''' Tests the reading of the robocopy- and status-logfiles '''
        files = run_msqc_pipeline._read_logfile(self.logfile)
        self.assertEquals(files, {'QE1_120315_OPL000_26_MSQC_40min_200.raw': 'completed',
		                          'QE2_120521_OPL2012_EH_barf1_serum_A.raw': 'completed'})
        files = run_msqc_pipeline._parse_robocopy_log(self.robocopylog, files)
        self.assertEquals(files, {'QE1_120315_OPL000_26_MSQC_40min_200.raw': 'completed',
		                          'QE2_120521_OPL2012_EH_barf1_serum_A.raw': 'completed',
								  'U87_10mg_B.raw': 'new'})

    def test_raw_format_conversions(self):
        ''' Test the conversion of RAW files into mzXML / MGF files '''
        run_msqc_pipeline._raw_format_conversions(self.rawfile, self.temp_folder)

        # Check if output paths for the mzXML and MGF files exist
        mzxml_file = os.path.join(self.temp_folder, self.rawfilebase + '.RAW.mzXML')
        self.failUnless(os.path.exists(mzxml_file))

        # Disabled while NIST not running
        # mgf_file = os.path.join(self.temp_folder, self.rawfilebase + '.RAW.MGF')
        # self.failUnless(os.path.exists(mgf_file))

    def test_get_filelist(self):
        ''' Test the detection of new RAW files to process '''
        # Create a set of (empty) RAW files
        tmp_file_list = []
        for i in range(5):
            filename = tempfile.mkstemp(suffix='.RAW', prefix='test_raw_file', dir=self.temp_folder)[1]
            open(filename).close()
            tmp_file_list.append(filename)
        # Override location of the progress log defined as global variable in run_msqc_pipeline
        run_msqc_pipeline._PROGRES_LOG = self.logfile
        # Detect files
        detected_files = set(run_msqc_pipeline._get_filelist(self.temp_folder, None))
        # Extract file names from created files (which is a tuple of (number, /full/path/to/tmp_file.RAW))
        temp_files = set(os.path.basename(f) for f in tmp_file_list)
        # Compare with list of created temp files
        self.assertEqual(temp_files, detected_files)

    def test_manage_paths(self):
        ''' Tests the creation of the path to the report '''
        # Set globally defined _WEB_DIR to temp folder
        run_msqc_pipeline._WEB_DIR = self.temp_folder
        # Create directory tree
        run_msqc_pipeline._manage_paths('temp_report')
        # Get date (year, month)
        ctime = gmtime()
        year = strftime("%Y", ctime)
        month = strftime("%b", ctime)
        # Expected path
        path = os.path.normpath('%s/%s/%s/temp_report/' % (self.temp_folder, year, month))
        self.failUnless(os.path.exists(path))

    def _defunct_test_run_nist(self):
        ''' Run NIST, and assure the msqc file is output to the correct path.'''
        # Copy NIST input files to temp folder
        copy(self.rawfile, self.temp_folder)
        copy(self.mzxmlfile, self.temp_folder)
        copy(self.mgffile, self.temp_folder)

        # Run NIST in temp folder
        restore_path = os.getcwd()
        os.chdir(self.temp_folder)
        rawfile = os.path.join(self.temp_folder, self.rawfilebase + '.RAW')
        run_msqc_pipeline._run_nist(rawfile, self.temp_folder)
        os.chdir(restore_path)

        # Check output path exists
        msqc_file = os.path.join(self.temp_folder, self.rawfilebase + '.msqc')
        self.failUnless(os.path.exists(msqc_file))

    def test_run_r_script(self):
        ''' Run the R script generating the graphics '''
        copy(self.mzxmlfile, self.temp_folder)
        run_msqc_pipeline._run_r_script(self.temp_folder, self.temp_folder, self.rawfilebase)

        # Test if graphics and logfile exists
        heatmaps = [self.rawfilebase + '_heatmap.png', self.rawfilebase + '_heatmap.pdf']
        ionplots = [self.rawfilebase + '_ions.png', self.rawfilebase + '_ions.pdf']
        rlog = os.path.join(self.temp_folder, self.rawfilebase + '.RLOG')

        self.failUnless([os.path.exists(x) for x in heatmaps])
        self.failUnless([os.path.exists(x) for x in ionplots])
        self.failUnless(os.path.exists(rlog))

    def test_create_report(self):
        ''' Tests the creation of the final report file '''
        # We need to first create the complete metrics dictionary
        metrics_file = resource_filename(__name__, "data/nist_metrics.msqc")
        nist_metrics = parse_metrics._extract_nist_metrics(metrics_file)
        # Add generic metrics
        nist_metrics['generic'] = parse_metrics._extract_generic_metrics(self.rawfile, time.time())
        # Create report (move to the QC directory, otherwise template is not found
        restore_path = os.getcwd()
        os.chdir('QC')
        run_msqc_pipeline._create_report(self.temp_folder, self.rawfilebase, nist_metrics)
        # Test for presence of the report file
        report_file = '{0}/{1}'.format(self.temp_folder, 'index.html')
        self.failUnless(os.path.exists(report_file))
        # Return to the test directory
        os.chdir(restore_path)

    def tearDown(self):
        ''' Cleans up tests '''
        shutil.rmtree(self.temp_folder)

if __name__ == '__main__':
    unittest.main()
