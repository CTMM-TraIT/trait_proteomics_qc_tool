package nl.ctmm.trait.proteomics.qcviewer.input;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import nl.ctmm.trait.proteomics.qcviewer.gui.ChartUnit;
import nl.ctmm.trait.proteomics.qcviewer.utils.Constants;

import org.apache.commons.io.FilenameUtils;
import org.jfree.data.xy.XYSeries;
import org.joda.time.Interval;

/**
 * This class contains the logic to read the directory/file structure and prepare data to be displayed.
 * 
 * TODO: Use Map<String, ReportUnit> instead of ArrayList<ReportUnit>. [Pravin]
 * 
 * @author <a href="mailto:pravin.pawar@nbic.nl">Pravin Pawar</a>
 * @author <a href="mailto:freek.de.bruijn@nbic.nl">Freek de Bruijn</a>
 */
public class ReportReader {
    /**
     * The logger for this class.
     */
    private static final Logger logger = Logger.getLogger(ReportReader.class.getName());

    /**
     * The names of the directories the QC pipeline uses to represent the months.
     */
    private static final List<String> MONTH_DIRS = Arrays.asList(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    );

    /**
     * Message written to the logger while creating report unit.
     */
    private static final String CREATING_REPORT_UNIT_MESSAGE = 
                                    "Creating report unit No. %s for msrun %s";

    /**
     * Message written to the logger while adding report unit.
     */
    private static final String ADDED_REPORT_MESSAGE = "Added report with msrunName %s";

    /**
     * Message written to the logger while skipping report unit.
     */
    private static final String SKIPPED_REPORT_MESSAGE = "Skipped report with msrunName %s " 
                                        + " runningMsrunName %s.";

    /**
     *  Pattern used to confirm whether yearFileName is a 4 digit number or not.
     */
    private static final String YEAR_NUMBER_PATTERN = "[0-9][0-9][0-9][0-9]";

    /**
     * Message to log when an exception occurs while reading a TIC matrix file.
     */
    private static final String EXCEPTION_GRAPH_SERIES_MESSAGE = 
                    "Something went wrong while reading graph series data";

    /**
     * The current report number.
     */
    private int currentReportNum;

    /**
     * The reader used to get the metrics values from the metrics JSON files.
     */
    private JsonMetricsReader jsonMetricsReader;

    /**
     * Construct a report reader that can search for QC reports.
     *
     * @param metricsParser the metrics parser to use.
     */
    public ReportReader(final MetricsParser metricsParser) {
        jsonMetricsReader = new JsonMetricsReader(metricsParser);
    }

    /**
     * Search through the directories under the root directory for files generated by the QC tool and return the
     * relevant data.
     *
     * @param rootDirectoryName the root directory that contains the year directories.
     * @param runningMsrunName the ms run name of the raw file that is currently being processed by the QC pipeline.
     * @param reportUnitsKeys the msrun names of the reports that are already retrieved in the past.
     * @param fromDate the start of the date range to search.
     * @param tillDate the end of the date range to search.
     * @return a list with report units.
     */
    public Map<String, ReportUnit> retrieveReports(final String rootDirectoryName, final String runningMsrunName,
                                                   final List<String> reportUnitsKeys, final Date fromDate,
                                                   final Date tillDate) {
        /*The directory has three levels - year, month and msrun.
        The msrun directory may contain following two files of importance:
        1) metrics.json: String file containing values of all QC metrics in json object format 
        2) msrun*_ticmatrix.csv: File containing list of x and y coordinates of the TIC chart 
        */
        currentReportNum = (reportUnitsKeys != null) ? reportUnitsKeys.size() : 0;
        final Map<String, ReportUnit> reportUnitsTable = new HashMap<>();
        logger.log(Level.ALL, "Root folder = " + rootDirectoryName);
        for (final File yearDirectory : getYearDirectories(FilenameUtils.normalize(rootDirectoryName))) {
            logger.fine("Year = " + yearDirectory.getName());
            for (final File monthDirectory : getMonthDirectories(yearDirectory)) {
                logger.fine("Month = " + monthDirectory.getName());
                for (final File msRunDirectory : getMsRunDirectories(monthDirectory)) {
                    logger.fine("Msrun = " + msRunDirectory.getName());
                    if (new Interval(fromDate.getTime(), tillDate.getTime()).contains(msRunDirectory.lastModified())) {
                        final File[] dataFiles = msRunDirectory.listFiles();
                        //Check whether "metrics.json", and "*_ticmatrix.csv" files are available
                        final String errorString = checkDataFilesAvailability(dataFiles);
                        final boolean errorFlag = !"".equals(errorString);
                        final String msrunName = msRunDirectory.getName().trim();
                        if (!skipMsrun(msrunName, runningMsrunName, reportUnitsKeys)) {
                            final ReportUnit report = createReportUnit(msRunDirectory.getName(), dataFiles, errorFlag,
                                                                       errorString);
                            reportUnitsTable.put(msrunName, report);
                            logger.fine(String.format(ADDED_REPORT_MESSAGE, msrunName));
                        } else {
                            logger.fine(String.format(SKIPPED_REPORT_MESSAGE, msrunName, runningMsrunName));
                        }
                    } 
                }
            }
        }
        return reportUnitsTable;
    }

    /**
     * Check whether the report directory contains "metrics.json", and "_ticmatrix.csv" files.
     *
     * @param dataFiles list of files in folder msrunName.
     * @return errorString if the "metrics.json", and "_ticmatrix.csv" files not found.
     */
    private String checkDataFilesAvailability(final File[] dataFiles) {
        boolean metrics = false;
        boolean ticMatrix = false;
        String errorString = "";
        for (final File dataFile : dataFiles) {
            final String dataFileName = dataFile.getName();
            if (dataFile.isFile()) {
                logFileName(dataFileName);
                if (Constants.METRICS_JSON_FILE_NAME.equals(dataFileName)) {
                    metrics = true;
                } else if (dataFileName.endsWith(Constants.TIC_MATRIX_FILE_NAME_SUFFIX)) {
                    ticMatrix = true;
                }
            }
        }
        if (!metrics || !ticMatrix) {
            errorString = Constants.REPORT_ERROR_MISSING_DATA;
        }
        return errorString; 
    }

    /**
     * Determine whether a msrun should be skipped.
     *
     * @param msrunName the name of the msrun.
     * @param runningMsrunName the ms run name of the raw file that is currently being processed by the QC pipeline.
     * @param reportKeys the msrun names of the reports that are already retrieved in the past.
     * @return whether the msrun should be skipped.
     */
    private boolean skipMsrun(final String msrunName, final String runningMsrunName, final List<String> reportKeys) {
        return runningMsrunName.equals(msrunName) || ((reportKeys != null) && reportKeys.contains(msrunName));
    }

    /**
     * Log the name of a directory that was detected.
     *
     * @param directoryName the name of the file.
     */
    private void logDirectoryName(final String directoryName) {
        logger.fine("Directory " + directoryName);
    }

    /**
     * Log the name of a file that was detected.
     *
     * @param fileName the name of the file.
     */
    private void logFileName(final String fileName) {
        logger.fine("File " + fileName);
    }

    /**
     * Retrieve the year directories in the root directory.
     *
     * @param rootDirectoryName the name of the root directory in which to search for year directories.
     * @return the list of year directories.
     */
    private List<File> getYearDirectories(final String rootDirectoryName) {
        final List<File> yearDirectories = new ArrayList<>();
        final File rootDirectory = new File(FilenameUtils.normalize(rootDirectoryName));
        if (rootDirectory.exists()) {
            final File[] yearFiles = rootDirectory.listFiles();
            if (yearFiles != null) {
                for (final File yearFile : yearFiles) {
                    if (isYearDirectory(yearFile)) {
                        yearDirectories.add(yearFile);
                    }
                }
            }
        }
        return yearDirectories;
    }

    /**
     * Check whether a year file object is a directory with a four digit name.
     *
     * @param yearFile the year file object.
     * @return whether a year file object is a year directory.
     */
    private boolean isYearDirectory(final File yearFile) {
        boolean isYearDirectory = false;
        final String yearFileName = yearFile.getName();
        if (yearFile.isFile()) {
            logFileName(yearFileName);
        } else if (yearFile.isDirectory()) {
            logDirectoryName(yearFileName);
            // Confirm whether yearFileName is a 4 digit number or not.
            if (Pattern.compile(YEAR_NUMBER_PATTERN).matcher(yearFileName).matches()) {
                isYearDirectory = true;
            }
        }
        return isYearDirectory;
    }

    /**
     * Retrieve the month directories in the year directory.
     *
     * @param yearDirectory the year directory in which to search for month directories.
     * @return the list of month directories.
     */
    private List<File> getMonthDirectories(final File yearDirectory) {
        final List<File> monthDirectories = new ArrayList<>();
        final File[] monthFiles = yearDirectory.listFiles();
        if (monthFiles != null) {
            for (final File monthFile : monthFiles) {
                if (monthFile.isDirectory() && MONTH_DIRS.contains(monthFile.getName())) {
                    monthDirectories.add(monthFile);
                }
            }
        }
        return monthDirectories;
    }

    /**
     * Retrieve the MS run directories in the month directory.
     *
     * @param monthDirectory the month directory in which to search for MS run directories.
     * @return the list of MS run directories.
     */
    private List<File> getMsRunDirectories(final File monthDirectory) {
        final ArrayList<File> msRunDirectories = new ArrayList<>();
        final File[] msRunFiles = monthDirectory.listFiles();
        if (msRunFiles != null) {
            for (final File msRunFile : msRunFiles) {
                if (msRunFile.isDirectory()) {
                    msRunDirectories.add(msRunFile);
                }
            }
        }
        return msRunDirectories;
    }

    /**
     * Create a report unit and fill it with data from an array of files.
     *
     * We use two specific files:
     * 1) metrics.json: String file containing values of all QC metrics in json object format
     * e.g. {"generic": {"date": "2012/Nov/07 - 14:18", "ms2_spectra": ["MS2 Spectra", "22298 (22298)"],
     * "runtime": "0:16:23", "f_size": ["File Size (MB)", "830.9"],
     * "ms1_spectra": ["MS1 Spectra", "7707 (7707)"]}}
     * 2) msrun*_ticmatrix.csv: CSV file containing x and y axis values for drawing ticGraph
     * 
     *  // TODO: we can detect errors by checking the metricsValues and ticChartUnit 
     *  fields of the report unit. [Freek]
     * 
     * @param msrunName the name of the msrun.
     * @param dataFiles the files used to initialize the report unit.
     * @param errorFlag whether an error occurred while reading the files.
     * @param errorString if the "metrics.json", and "_ticmatrix.csv" files not found.
     * @return the new report unit.
     */
    private ReportUnit createReportUnit(final String msrunName, final File[] dataFiles, final boolean errorFlag,
                                        final String errorString) {
        currentReportNum++;
        logger.fine(String.format(CREATING_REPORT_UNIT_MESSAGE, currentReportNum, msrunName));
        final ReportUnit reportUnit = new ReportUnit(msrunName, currentReportNum);
        reportUnit.setErrorFlag(errorFlag); 
        reportUnit.setReportErrorString(errorString);
        for (final File dataFile : dataFiles) {
            final String dataFileName = dataFile.getName();
            if (dataFile.isFile()) {
                handleQCPipelineDataFile(msrunName, reportUnit, dataFile, dataFileName);
            } else if (dataFile.isDirectory()) {
                logDirectoryName(dataFileName);
            }
        }
        return reportUnit;
    }

    /**
     * Handle a data file from the QC pipeline and initialize the report unit with it.
     *
     * @param msrunName the name of the msrun.
     * @param reportUnit the report unit that is being created.
     * @param dataFile a data file that is used to initialize the report unit.
     * @param dataFileName the name of the data file.
     */
    private void handleQCPipelineDataFile(final String msrunName, final ReportUnit reportUnit, final File dataFile,
                                          final String dataFileName) {
        logFileName(dataFileName);
        if (Constants.METRICS_JSON_FILE_NAME.equals(dataFileName)) {
            Map<String, String> metricsValues = jsonMetricsReader.readJsonValuesForGenericAndNISTMetrics(dataFile);
            metricsValues = jsonMetricsReader.readJsonValuesForQuaMeterIDFreeMetrics(dataFile, metricsValues); 
            reportUnit.setMetricsValues(metricsValues);
            //One or more metricsValues are missing if any of the values is "N/A"
            if (metricsValues.containsValue(Constants.NOT_AVAILABLE_STRING)) {
                reportUnit.setErrorFlag(true);
                reportUnit.setReportErrorString(Constants.REPORT_ERROR_MISSING_DATA);
            }
        } else if (dataFileName.endsWith(Constants.TIC_MATRIX_FILE_NAME_SUFFIX)) {
            reportUnit.createChartUnit(readXYSeries(msrunName, dataFile));
            final ChartUnit ticChartUnit = reportUnit.getChartUnit();
            //If value maxTicIntensity is 0 then there is missing data in ChartUnit
            if (ticChartUnit.getMaxTicIntensity() == 0) {
                reportUnit.setErrorFlag(true);
                reportUnit.setReportErrorString(Constants.REPORT_ERROR_MISSING_DATA);
            }
        }
    }

    /**
     * Create XYSeries by reading TIC matrix file that contains X & Y axis values representing TIC graph.
     *
     * @param msrunName the name of the msrun.
     * @param ticMatrixFile the tic matrix file to read from.
     * @return XYSeries.
     */
    private XYSeries readXYSeries(final String msrunName, final File ticMatrixFile) {
        final XYSeries series = new XYSeries(msrunName);
        try {
            final BufferedReader bufferedReader = new BufferedReader(new FileReader(ticMatrixFile));
            //skip first line with column names: "rt","ions"
            bufferedReader.readLine();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                final StringTokenizer lineTokenizer = new StringTokenizer(line, ",");
                // The first token is the x value.
                // TODO: make sure the rt value is in seconds. [Freek]
                final float x = Float.parseFloat(lineTokenizer.nextToken()) / Constants.SECONDS_PER_MINUTE;
                // The second token is the y value.
                final float y = Float.parseFloat(lineTokenizer.nextToken());
                series.add(x, y);
            }
            bufferedReader.close();
        } catch (final NumberFormatException | IOException e) {
            logger.log(Level.SEVERE, EXCEPTION_GRAPH_SERIES_MESSAGE, e);
        }
        return series;
    }
}
