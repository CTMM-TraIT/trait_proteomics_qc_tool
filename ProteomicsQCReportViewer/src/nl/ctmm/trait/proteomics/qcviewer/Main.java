package nl.ctmm.trait.proteomics.qcviewer;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import nl.ctmm.trait.proteomics.qcviewer.gui.DataEntryForm;
import nl.ctmm.trait.proteomics.qcviewer.gui.ViewerFrame;
import nl.ctmm.trait.proteomics.qcviewer.input.MetricsParser;
import nl.ctmm.trait.proteomics.qcviewer.input.ProgressLogMonitor;
import nl.ctmm.trait.proteomics.qcviewer.input.ProgressLogReader;
import nl.ctmm.trait.proteomics.qcviewer.input.ReportReader;
import nl.ctmm.trait.proteomics.qcviewer.input.ReportUnit;
import nl.ctmm.trait.proteomics.qcviewer.utils.Constants;

import org.apache.commons.io.FilenameUtils;
import org.jfree.ui.RefineryUtilities;

/**
 * The class that starts the QC Report Viewer.
 *
 * TODO: move nl directory in source directory to source\main\java directory. [Freek]
 * TODO: move test directory to source\test\java directory. [Freek]
 * TODO: move images directory (with logo image files) to source\main\resources directory. [Freek]
 * TODO: change QE*.raw file names into less descriptive names (see ProgressLogReader.parseCurrentStatus). [Freek]
 *
 * @author <a href="mailto:pravin.pawar@nbic.nl">Pravin Pawar</a>
 * @author <a href="mailto:freek.de.bruijn@nbic.nl">Freek de Bruijn</a>
 */
public class Main {
    /**
     * The logger for this class.
     */
    private static final Logger logger = Logger.getLogger(Main.class.getName());
    /**
     * This is the singleton instance of this class.
     */
    private static final Main INSTANCE = new Main();

    /**
     * Message written to the logger when a report is skipped.
     */
    private static final String SKIPPED_REPORT_MESSAGE = "Skipped report unit %s. Logfile says it is running %s.";

    /**
     * Message written to the logger to show the number of reports.
     */
    private static final String NUMBER_OF_REPORTS_MESSAGE = "Number of reports is %s.";

    /**
     * Message written to the logger to show the updated number of reports.
     */
    private static final String NEW_NUMBER_OF_REPORTS_MESSAGE = "Updated %s entries. New number of reports is %s.";

    /**
     * Message written to the logger when no reports are found.
     */
    private static final String NO_REPORTS_MESSAGE = "No reports found in %s.";

    /**
     * The application properties such as root folder and default metrics to show.
     */
    private Properties applicationProperties;

    /**
     * The parser that can read all metrics supported by the NIST pipeline.
     */
    private MetricsParser metricsParser;

    /**
     * The current pipeline status like idle or currently analyzing ...
     */
    private String pipelineStatus = "";

    /**
     * Start of the date range of report units to include for display.
     */
    private Date fromDate;

    /**
     * End of the date range of report units to include for display.
     */
    private Date tillDate;

    /**
     * The main GUI frame of the viewer application.
     */
    private ViewerFrame frame;

    /**
     * Support for selecting the root directory to read the report units from and specifying the preferred date range.
     * <p/>
     * TODO: do we need to keep this around or can we construct it if needed? [Freek]
     */
    private DataEntryForm dataEntryForm;

    /**
     * The map that contains all current reports within the date range. New reports are added as they are generated by
     * the pipeline. The msrun names are used as keys in the map.
     */
    private Map<String, ReportUnit> reportUnitsTable = new HashMap<>();

    /**
     * Reader for the pipeline log file - qc_status.log from the preferredRootDirectory.
     */
    private ProgressLogReader progressLogReader;

    /**
     * Another way to monitor the pipeline log file - qc_status.log from the preferredRootDirectory.
     * <p/>
     * TODO: this can probably be removed because ProgressLogReader is sufficient. Check with Sander and Thang first.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private ProgressLogMonitor progressLogMonitor;

    /**
     * The directory to which the QC pipeline writes the QC reports.
     */
    private String preferredRootDirectory;

    /**
     * The constructor is private so only the singleton instance can be used.
     */
    private Main() {
    }

    /**
     * The starting method for the QC Report Viewer.
     *
     * @param arguments the command-line arguments, which are currently not used.
     */
    // CHECKSTYLE_OFF: UncommentedMain
    public static void main(final String[] arguments) {
        getInstance().runReportViewer();
    }
    // CHECKSTYLE_ON: UncommentedMain

    /**
     * Get the main instance.
     *
     * @return the main instance.
     */
    public static Main getInstance() {
        return INSTANCE;
    }

    /**
     * Start the QC Report Viewer.
     * <p/>
     * TODO: see whether we can update the application instead of restarting it. [Freek] Added updateReportViewer method [Pravin]
     */
    public void runReportViewer() {
        prepareAllLoggers();
        applicationProperties = loadProperties();
        metricsParser = new MetricsParser(applicationProperties);
        preferredRootDirectory = applicationProperties.getProperty(Constants.PROPERTY_ROOT_FOLDER);
        logger.fine("in Main preferredRootDirectory = " + preferredRootDirectory);
        dataEntryForm = new DataEntryForm(this, applicationProperties);
        dataEntryForm.setRootDirectoryName(preferredRootDirectory);
        dataEntryForm.displayInitialDialog();
        //Determine fromDate and TillDate range to select the reports
        determineReportDateRange();
        progressLogOperations();
        //Obtain initial set of reports according to date filter
        ArrayList<ReportUnit> displayableReportUnits = processInitialReports();
        logger.fine(String.format(NUMBER_OF_REPORTS_MESSAGE, reportUnitsTable.size()));
        dataEntryForm.disposeInitialDialog();
        //Start main user interface
        startQCReportViewerGui(applicationProperties, displayableReportUnits, pipelineStatus);
        if (displayableReportUnits.size() == 0) {
            // There are no reports in the current root directory. Obtain new directory location from the user. 
            dataEntryForm.displayErrorMessage(String.format(NO_REPORTS_MESSAGE, preferredRootDirectory));
            dataEntryForm.displayRootDirectoryChooser();
        } 
    }
    
   	/**
     * Determine the date range for displaying reports.
     */
    private void determineReportDateRange() {
        Constants.DATE_FORMAT.setLenient(false);
        final String reportsFromDate = applicationProperties.getProperty(Constants.PROPERTY_SHOW_REPORTS_FROM_DATE);
        final String reportsTillDate = applicationProperties.getProperty(Constants.PROPERTY_SHOW_REPORTS_TILL_DATE);
        if (!"".equals(reportsFromDate.trim()) && !"".equals(reportsFromDate.trim())) {
            // Dates are specified. Now check if the dated are valid.
            try {
                fromDate = Constants.DATE_FORMAT.parse(reportsFromDate);
                tillDate = Constants.DATE_FORMAT.parse(reportsTillDate);
            } catch (final ParseException e) {
                fromDate = null;
                tillDate = null;
                logger.log(Level.SEVERE, "Something went wrong while processing fromDate and tillDate", e);
            }
        }
        if (tillDate == null) {
            // The date interval is not specified: make it the last two weeks.
            final Calendar now = Calendar.getInstance();
            now.add(Calendar.WEEK_OF_YEAR, -2);
            fromDate = now.getTime();
            tillDate = Calendar.getInstance().getTime();
        }
        logger.fine("fromDate = " + Constants.DATE_FORMAT.format(fromDate) + " tillDate = "
                    + Constants.DATE_FORMAT.format(tillDate));
    }

	/**
     * Determine progress log file path
     * Setup progressLogReader to read current pipeline status
     * Setup progressLogMonitor to monitor changes to progress log file 
     */
    private void progressLogOperations() {
        final String progressLogFilePath = FilenameUtils.normalize(preferredRootDirectory + "\\"
                + Constants.PROGRESS_LOG_FILE_NAME);
        logger.fine("progressLogFilePath = " + progressLogFilePath);
        progressLogReader = ProgressLogReader.getInstance(); 
        progressLogReader.setProgressLogFile(progressLogFilePath);
        pipelineStatus = progressLogReader.getCurrentStatus();
        //Start the progress log monitor to monitor qc_status.log file
        // TODO: keep a reference to this progressLogMonitor (declare as a field)? [Freek]
        /*progressLogMonitor = ProgressLogMonitor.getInstance();
        try {
            progressLogMonitor.addFileChangeListener(progressLogReader, progressLogFilePath,
                                                     Constants.POLL_INTERVAL_PIPELINE_LOG);
        } catch (final FileNotFoundException e1) {
            e1.printStackTrace();
            logger.fine("progress log file not found. Configured path: " + progressLogFilePath);
        } //Refresh period is 5 seconds*/
	}



    /**
     * Prepare the loggers for this application:
     * - set ConsoleHandler as handler.
     * - set logging level to ALL.
     */
    private void prepareAllLoggers() {
        final ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        final Logger rootLogger = Logger.getLogger("nl.ctmm.trait.proteomics.qcviewer");
        rootLogger.setLevel(Level.ALL);
        rootLogger.addHandler(handler);
    }

    /**
     * Load the application properties from the properties file.
     *
     * @return the application properties.
     */
    private Properties loadProperties() {
        final Properties appProperties = new Properties();
        // Set a default for root folder property.
        appProperties.setProperty(Constants.PROPERTY_ROOT_FOLDER, Constants.DEFAULT_ROOT_FOLDER);
        // Load the actual properties from the property file.
        try {
            final String fileName = FilenameUtils.normalize(Constants.PROPERTIES_FILE_NAME);
            final FileInputStream fileInputStream = new FileInputStream(fileName);
            appProperties.load(fileInputStream);
            fileInputStream.close();
        } catch (final IOException e) {
            logger.log(Level.SEVERE, "Loading of application properties failed.", e);
        }
        return appProperties;
    }

    /**
     * Read initial set of QC Reports from the preferredRootDirectory. The reports are filtered according to date
     * criteria
     * @return displayableReportUnits: QC reports to be displayed in the report viewer
     * TODO: look at similarities between processInitialReports and notifyProgressLogFileChanged.
     */
    private ArrayList<ReportUnit> processInitialReports() {
    	logger.fine("Main processInitialReports()");
        final ArrayList<ReportUnit> reportUnits = getReportUnits(preferredRootDirectory, fromDate, tillDate);
        final String runningMsrunName = progressLogReader.getRunningMsrunName();
        final ArrayList<ReportUnit> displayableReportUnits = new ArrayList<>();
        //Reinitialize reportUnitsTable
        reportUnitsTable = new HashMap<>();
        //populate reportUnitsTable
        logger.fine("All reportUnitsSize = " + reportUnits.size() + " runningMsrunName = " + runningMsrunName);
        for (final ReportUnit thisUnit : reportUnits) {
            final String thisMsrun = thisUnit.getMsrunName();
            if (!thisMsrun.equals(runningMsrunName)) {
            	thisUnit.setReportNum(reportUnitsTable.size() + 1);
            	displayableReportUnits.add(thisUnit);
                //for identifying duplicate reports
                if (reportUnitsTable.containsKey(thisMsrun)) {
                    logger.warning("Alert!! Already exists in ReportUnitsTable " + thisMsrun);
                }
                //Update reportUnit in reportUnitsTable
                reportUnitsTable.put(thisMsrun, thisUnit);
            } else {
                // Currently processing this msrun. Do not include in the list of reports.
                logger.fine(String.format(SKIPPED_REPORT_MESSAGE, thisMsrun, runningMsrunName));
            }
        }
        return displayableReportUnits;
	}

	/**
     * Progress log file has changed. Refresh the application automatically on this notification.
     *
     * @param newPipelineStatus the new status of the QC pipeline.
     */
    public void notifyProgressLogFileChanged(final String newPipelineStatus) {
        /* The tillDate has to be updated as currentTime - since the pipeline status has changed.
        * FromDate could be specified by the user
        */
        final String runningMsrunName = progressLogReader.getRunningMsrunName();
        tillDate = Calendar.getInstance().getTime();
        // TODO: can we use the preferredRootDirectory field below? [Freek] Yes & done [Pravin]
        final List<ReportUnit> reportUnits = getReportUnits(preferredRootDirectory, fromDate, tillDate);
        if (reportUnits.size() == 0) {
            // There exist no reports in current root directory.
            // Get new location to read reports from.
            dataEntryForm.displayErrorMessage(String.format(NO_REPORTS_MESSAGE, preferredRootDirectory));
            dataEntryForm.displayRootDirectoryChooser();
        } else {
            //Compare newReportUnits with reportUnits
            final ArrayList<ReportUnit> newReportUnits = new ArrayList<>();
            int numUpdates = 0;
            for (final ReportUnit thisUnit : reportUnits) {
                //if not in reportUnits, then add to newReportUnits
                final String thisMsrun = thisUnit.getMsrunName();
                if (!thisMsrun.equals(runningMsrunName)) {
                    // Can someone explain the comment below? [Freek] - Explained [Pravin]
                    /* The pipeline is currently processing runningMsrunName. e.g. 
                     * 2013-06-04 13:40:01.165000    QE2_101109_OPL0004_TSV_mousecelllineL_Q1_2.raw    running
                     * The QC report is being generated. 
                     * Hence do not add this report yet to the reportUnitsTable.  
                     */
                    if (reportUnitsTable.containsKey(thisMsrun)) {
                        final ReportUnit existingUnit = reportUnitsTable.get(thisMsrun);
                        thisUnit.setReportNum(existingUnit.getReportNum());
                        reportUnitsTable.remove(thisMsrun);
                        reportUnitsTable.put(thisMsrun, thisUnit);
                        numUpdates++;
                    } else {
                        logger.fine("Does not exist in reportUnitsTable. " + thisUnit.getMsrunName()
                                    + " Adding to new report units with reportNum " + (reportUnitsTable.size() + 1));
                        thisUnit.setReportNum(reportUnitsTable.size() + 1);
                        newReportUnits.add(thisUnit);
                        //Add to hashTable
                        reportUnitsTable.put(thisUnit.getMsrunName(), thisUnit);
                    }
                } else {
                    // Currently processing this msrun. Do not include in the list of reports.
                    logger.fine(String.format(SKIPPED_REPORT_MESSAGE, thisMsrun, runningMsrunName));
                }
            }
            logger.fine(String.format(NUMBER_OF_REPORTS_MESSAGE + " " + NEW_NUMBER_OF_REPORTS_MESSAGE,
                                      reportUnitsTable.size(), numUpdates, newReportUnits.size()));
            reportUnits.clear();
            //Refresh ViewerFrame with new Report Units
            frame.updateReportUnits(newReportUnits, newPipelineStatus, false);
        }
    }

    /**
     * Get the report units from the directory structure below the root directory.
     *
     * @param rootDirectoryName the root directory to search in.
     * @param fromDate          the start of the date range to search.
     * @param tillDate          the end of the date range to search.
     * @return the list with report units.
     */
    private ArrayList<ReportUnit> getReportUnits(final String rootDirectoryName, final Date fromDate, final Date tillDate) {
        return new ReportReader(metricsParser).retrieveReports(rootDirectoryName, fromDate, tillDate);
    }

    /**
     * Received a notification about a possible change in the QC pipeline status. If the status has indeed changed, push
     * the new pipeline status to the report viewer.
     *
     * @param newPipelineStatus updated pipeline status as read from the qc_status.log file.
     */
    public void notifyUpdatePipelineStatus(final String newPipelineStatus) {
        if (!pipelineStatus.equals(newPipelineStatus)) {
            pipelineStatus = newPipelineStatus;
            if (frame != null) {
                frame.updatePipelineStatus(pipelineStatus);
            }
        }
    }

    /**
     * Create and start the GUI - of the report viewer.
     *
     * @param appProperties  the application properties.
     * @param reportUnits    the report units to be displayed.
     * @param pipelineStatus the current status of the pipeline.
     */
    private void startQCReportViewerGui(final Properties appProperties, final List<ReportUnit> reportUnits,
                                        final String pipelineStatus) {
        logger.fine("Main startQCReportViewerGui");
        final List<String> qcParamNames = getColumnNames(appProperties, Constants.PROPERTY_TOP_COLUMN_NAMESV2);
        //Create ViewerFrame and set it visible
        frame = new ViewerFrame(metricsParser, appProperties, Constants.APPLICATION_TITLE, reportUnits, qcParamNames,
                                pipelineStatus);
        frame.pack();
        RefineryUtilities.centerFrameOnScreen(frame);
        frame.setVisible(true);
    }

    /**
     * Get a property with a comma-separated string with column names and convert it into a list of strings.
     *
     * @param applicationProperties the application properties.
     * @param propertyName          the name of the property that contains the comma-separated string with column names.
     * @return the column names in a list of strings.
     */
    private List<String> getColumnNames(final Properties applicationProperties, final String propertyName) {
        return Arrays.asList(applicationProperties.getProperty(propertyName).split(","));
    }
    
    /**
     * Update all the reports in the QC Report Viewer in the following two cases: 
     * 1) New root directory is chosen
     * 2) Change in the date range  
     */
	public void updateReportViewer() {
		logger.fine("Main updateReportViewer"); 
        applicationProperties = loadProperties();
        preferredRootDirectory = applicationProperties.getProperty(Constants.PROPERTY_ROOT_FOLDER);
		determineReportDateRange();
		final ArrayList<ReportUnit> displayableReportUnits = processInitialReports();
		if (displayableReportUnits.size() == 0) {
	        // There exist no reports in selected root directory conforming date range
	        // Get new location to read reports from.
	        dataEntryForm.displayErrorMessage(String.format(NO_REPORTS_MESSAGE, preferredRootDirectory));
	        dataEntryForm.displayRootDirectoryChooser();
	    } else {
			progressLogOperations();
			final String pipelineStatus = progressLogReader.getCurrentStatus();
	        //Refresh ViewerFrame with new Report Units
	        frame.updateReportUnits(displayableReportUnits, pipelineStatus, true);
	    }
	}
}
