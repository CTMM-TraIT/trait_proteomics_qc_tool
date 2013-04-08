#!/Perl/bin/Perl.exe

use strict;
use Getopt::Long;
use lib '.';
use MetricsPipeline;
use ParseMetrics;

# NIST Mass Spectrometry Data Center
# Paul A. Rudnick
# paul.rudnick@nist.gov
# 09/09/09

# Pipeline program to generate NIST LC-MS/MS QC metrics from Thermo Raw
# files.

#**
# Data analysis of Agilent QTOF, Orbitrap HCD, and Orbitrap CID were added.
# 2011-5-20
# Xiaoyu(Sara) Yang
#*

#### Globals
my $version = '1.2.2beta';
my $version_date = '08-01-2011';

### Read command-line parameters
my @in_dirs = (); 
my @libs = (); 
my (
    $out_dir,
    # $report_name, depracated 2/4/2010
    # $out_file depracated 1/25/2010
    $instrument_type,
    $fasta,
    $overwrite_all,
    $overwrite_searches,
    $no_peptide,
    $pro_ms,
    $mcp_summary,
    $help,
    $log_file,
    $ini_tag,
    $target_file,
    $verbose,
    $msconvert,
    $srmd,
    );
my $search_engine = 'mspepsearch'; # Default
my $sort_by = 'date'; # Default
my $mode = 'full';  # Default 

my $pl = new MetricsPipeline($version, $version_date);
my $cl; # Command-line used

if (!@ARGV) {
	$pl->usage();
	$pl->exiting();
} else {
	$cl = join(' ', @ARGV);
}

# Command-line specification

if (!GetOptions (
	'in_dir=s' => \@in_dirs,
	'out_dir=s' => \$out_dir,
	#'report_name=s' => \$report_name,
	'library=s' => \@libs,
	'instrument_type=s' => \$instrument_type,
	'fasta:s' => \$fasta,
	'search_engine:s' => \$search_engine,
	'overwrite_all!' => \$overwrite_all,
	'overwrite_searches!' => \$overwrite_searches,
	'sort_by:s' => \$sort_by,
	'mode:s' => \$mode,
	'no_peptide!' => \$no_peptide,
	'pro_ms!' => \$pro_ms,
	'mcp_summary!' => \$mcp_summary,
	'help|?' => \$help,
	'log_file!' => \$log_file,
	'ini_tag:s' => \$ini_tag,
	'target_file:s' => \$target_file,
	'verbose!' => \$verbose,
	'msconvert!' => \$msconvert,
	'srmd!'	=> \$srmd,
	)
   ) {
	$pl->exiting();
}

$pl->set_base_paths(); # Must be run from scripts directory.

if ($ini_tag) {
	@ARGV = split(' ', $pl->get_cl_from_ini_tag($ini_tag));
	# Re-process options if ini_tag given.
	if (!GetOptions (
		'in_dir=s' => \@in_dirs,
		'out_dir=s' => \$out_dir,
		#'report_name=s' => \$report_name,
		'library=s' => \@libs,
		'instrument_type=s' => \$instrument_type,
		'fasta:s' => \$fasta,
		'search_engine:s' => \$search_engine,
		'overwrite_all!' => \$overwrite_all,
		'overwrite_searches!' => \$overwrite_searches,
		'sort_by:s' => \$sort_by,
		'mode:s' => \$mode,
		'no_peptide!' => \$no_peptide,
		'pro_ms!' => \$pro_ms,
		'mcp_summary!' => \$mcp_summary,
		'help|?' => \$help,
		'log_file!' => \$log_file,
		'ini_tag:s' => \$ini_tag,
		'target_file:s' => \$target_file,
		'verbose!' => \$verbose,
		'msconvert!' => \$msconvert,
		'srmd!' => \$srmd,
		)
	   ) {
		$pl->exiting();
	}
}
if ($help) {
	$pl->usage();
	$pl->exiting();
}
# Required commnad-line arguments.
if (! $in_dirs[0] && !$ini_tag ) {
	print STDERR "One or more \'--in_dir\' is required.\n";
	$pl->exiting();
}
if (! $out_dir && !$ini_tag ) {
	print STDERR "Argument \'--out_dir\' is required.\n";
	$pl->exiting();
}

if (! $libs[0] && !$ini_tag ) {
	print STDERR "Argument \'--library\' is required.\n";
	$pl->exiting();
}
if (! $instrument_type && !$ini_tag ) {
	print STDERR "Argument \'--instrument_type\' is required.\n";
	$pl->exiting();
}

### Pipeline Configuration (advanced/debugging)
my $run_converter = 1; # Programs can be skipped by setting these to 0.
my $run_search_engine = 1;
my $run_nistms_metrics = 1;
my $num_matches = 5; # Number of ID's per spectrum to return by search engines (only top match counted)
my $mspepsearch_threshold = 450; # 450; # Score
my $spectrast_threshold = 0.45; # fval
my $omssa_threshold = 0.1; # E-value

# Currently allowable instrument and engines
my @instrument_types = ('LCQ', 'LXQ', 'LTQ', 'FT', 'ORBI', 'AGILENT_QTOF', 'ORBI_HCD'); # Allowable instrument types.
my @search_engines = ('mspepsearch', 'spectrast', 'omssa'); # Available search engines

# General search engine parameters
my $low_accuracy_pmt = 2; # used by set_instrument()
my $high_accuracy_pmt = 0.6; # Large but will catch +2, 13C containing peptides.

# OMSSA configurable parameters
my $omssa_semi = 1; # Run a second OMSSA semitryptic search
my $missed_cleavages = 2;
my $omssa_mods = '1,3,10,110'; # metox, cam, n-term aceytlation, pyro-glu

### End configuration
#### End Globals

# Initiate pipeline
$pl->set_global_configuration(
			      $cl,
			      $overwrite_all,
			      $overwrite_searches,
			      $run_converter,
			      $run_search_engine,
			      $run_nistms_metrics,
			      \@instrument_types,
			      \@search_engines,
			      $no_peptide,
			      $pro_ms,
			      $mcp_summary,
			      $log_file,
			      $ini_tag,
			      $target_file,
			      $verbose,
			      $msconvert,
			      $srmd,
			      );

# Validate instrument selection
if ( $pl->set_instrument($instrument_type, $high_accuracy_pmt, $low_accuracy_pmt) ) {
	$pl->exiting();
}
# Check ProMS configuration.
if ($pl->check_proms()) {
	$pl->exiting();
}
# check executible files
if ( $pl->check_executables() ) {
	$pl->exiting();
}

# Validate in_directories
if ( $pl->check_in_dirs(\@in_dirs) ) {
	$pl->exiting();
}

# Validate out_dir
if ( $pl->check_out_dir($out_dir) ) {
	$pl->exiting();
}

if ( $pl->check_report_location() ) {
	$pl->exiting();
}

# Search engine configuration, including setting hard thresholds
if ( $pl->set_search_engine($search_engine, $num_matches) ) {
	$pl->exiting();
}
if ($pl->search_engine() eq 'mspepsearch') {
	$pl->set_score_threshold($mspepsearch_threshold);
} elsif ($pl->search_engine() eq 'spectrast') {
	$pl->set_score_threshold($spectrast_threshold);
} elsif ($pl->search_engine() eq 'omssa') {
	$pl->set_score_threshold($omssa_threshold);
	$pl->configure_omssa($omssa_semi, $missed_cleavages, $omssa_mods);
} else {
	print STDERR "Search engine not identified.\nExiting.\n";
	$pl->exiting();
}

# Check/validate search libs
if ( (scalar(@libs)>1) && ($pl->search_engine() ne 'mspepsearch') ) {
	print STDERR "Searching multiple databases/libraries only allowed for MSPepSearch.\n";
	$pl->exiting();
}
if ( $pl->is_peptide() ) {
	if ( $pl->check_fastas($fasta, \@libs) ) {
		$pl->exiting();
	}
}
if ( $pl->check_search_libs(\@libs) ) {
	$pl->exiting();
}

# Set data file sort option
if ( $pl->set_sort_option($sort_by) ) {
	$pl->exiting();
}
## Set mode (Currently, default mode ONLY is recommended)
if ( $pl->set_mode($mode) ) {
	$pl->exiting();
}
if ( $pl->check_target_file() ) {
	$pl->exiting();
}
### Begin processing.

# Convert raw data
if ( $pl->running_converter() ) {
	print "NISTMSQC: Running converter.\n";
	if ( $pl->run_converter() ) {
		$pl->exiting();
	}
}

# Identify peptide MS/MS spectra with a search engine
if ( $pl->running_search_engine() ) {
	print "NISTMSQC: Running search engine.\n";
	if ( $pl->run_search_engine() ) {
		$pl->exiting();
	} else {
		print "NISTMSQC: Done with searches.\n";
	}
}
# Run ProMS as MS1 analysis option
if ( $pl->running_pro_ms() ) {
	print "NISTMSQC: Running ProMS.\n";
	if ($pl->run_pro_ms() ) {
		$pl->exiting();
	} else {
		print "NISTMSQC: Done running ProMS.\n";
	}
}

# Calculate metrics using output from the above programs
if ( $pl->running_nistms_metrics() ) {
	print "NISTMSQC: Running nistms_metrics.\n";
	if ( $pl->run_nistms_metrics() ) {
		$pl->exiting();
	} else {
		print "NISTMSQC: Done running nistms_metrics.\n";
	}
}

print "\n#--> NISTMSQC: Pipeline completed and exiting. <--#\n";

exit(0);
