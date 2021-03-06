package nl.ctmm.trait.proteomics.qcviewer.input;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import nl.ctmm.trait.proteomics.qcviewer.gui.ChartUnit;
import nl.ctmm.trait.proteomics.qcviewer.utils.Constants;

import org.jfree.data.xy.XYSeries;

/**
 * The <code>ReportUnit</code> class contains information from an MS run generated by the QC tool that are displayed by
 * the QC report viewer. The instances of this class each represent a report of a single msrun.
 *
 * @author <a href="mailto:pravin.pawar@nbic.nl">Pravin Pawar</a>
 * @author <a href="mailto:freek.de.bruijn@nbic.nl">Freek de Bruijn</a>
 */
public class ReportUnit {
    /**
     * Number of the report displayed in viewer.
     */
    private int reportNum = -1;
    
    /**
     * Index of the report displayed in viewer.
     */
    private int reportIndex = -1;
    
    /**
     * Name of processed RAW file.
     */
    private String msrunName = "";
    
    /**
     * Numeric representation of RAW file size.
     */
    private Double fileSize;
    
    /**
     * String representation of RAW file size.
     */
    private String fileSizeString = Constants.NOT_AVAILABLE_STRING;
    
    /**
     * Number of MS1 Spectra in the RAW file.
     */
    private String ms1Spectra = Constants.NOT_AVAILABLE_STRING;
    
    /**
     * Number of MS2 Spectra in the RAW file.
     */
    private String ms2Spectra = Constants.NOT_AVAILABLE_STRING;
    
    /**
     * Date and time at which QC pipeline has processed RAW file.
     *
     * TODO: rename to measuredDateTime? See also Constants: METRIC_KEY_MEASURED = SORT_KEY_DATE. [Freek]
     */
    private String measured = Constants.NOT_AVAILABLE_STRING;
    
    /**
     * Time taken by the QC pipeline to completely process RAW file.
     */
    private String runtime = Constants.NOT_AVAILABLE_STRING;

    /**
     * String describing report error if files or values are missing.
     */
    private String reportErrorString = "";
    
    /**
     * To signify that one or more files belonging to this report are missing.
     */
    private boolean errorFlag;

    /**
     * Metrics values (map from keys to values).
     */
    private Map<String, String> metricsValues;

    /**
     * ChartUnit to hold corresponding chart.
     */
    private ChartUnit ticChartUnit;

    /**
     * One row in the QC Report Viewer table corresponds to one QC ReportUnit.
     * Sets the number of this QC ReportUnit.
     *
     * @param msrunName the unique msrun name - also represents RAW file uniquely
     * @param reportNum the unique report number.
     */
    public ReportUnit(final String msrunName, final int reportNum) {
        this.msrunName = msrunName;
        this.reportNum = reportNum;
        reportIndex = reportNum - 1; 
        // Create default chart unit to handle problems due to missing series data.
        ticChartUnit = new ChartUnit(msrunName, reportIndex, null);
    }

    /**
     * Get value of metric based on metrics key - e.g. "dyn:ds-1a".
     *
     * @param key metrics key in String format.
     * @return value of metric.
     */
    public String getMetricsValueFromKey(final String key) {
        final boolean metricAvailable = metricsValues != null && metricsValues.containsKey(key);
        return metricAvailable ? metricsValues.get(key) : Constants.NOT_AVAILABLE_STRING;
    }

    /**
     * Get the value of parameter reportNum.
     *
     * @return Serial number of current ReportUnit.
     */
    public int getReportNum() {
        return reportNum;
    }
    
    /**
     * Get the value of parameter reportIndex.
     *
     * @return Index of the current ReportUnit.
     */
    public int getReportIndex() {
        return reportIndex;
    }

    /**
     * Get the value of parameter msrunName as a string.
     *
     * @return the unique msrun name - also represents RAW file uniquely.
     */
    public String getMsrunName() {
        return msrunName;
    }

    /**
     * Get the value of errorFlag.
     * 
     * @return value of errorFlag.
     */
    public boolean getErrorFlag() {
        return errorFlag; 
    }
    
    /**
     * Set the value of errorFlag.
     *
     * @param flag the new value for error flag.
     */
    public void setErrorFlag(final boolean flag) {
        errorFlag = flag; 
    }
    
    /**
     * Set report error string.
     *
     * @param reportErrorString error string.
     */
    public void setReportErrorString(final String reportErrorString) {
        this.reportErrorString = reportErrorString;
    }
    
    /**
     * Get report error string.
     *
     * @return report error string.
     */
    public String getReportErrorString() {
        return reportErrorString;
    }

    /**
     * Get the value of parameter fileSize as a string.
     *
     * @return size of the RAW MS data file (in MB)
     */
    public String getFileSizeString() {
        return fileSizeString;
    }

    /**
     * Get the value of parameter fileSize.
     *
     * @return size of the RAW MS data file (in MB).
     */
    public Double getFileSize() {
        return fileSize;
    }

    /**
     * Create ticChart and corresponding chart data for this report unit.
     *
     * @param series a sequence of (x, y) data items.
     */
    public void createChartUnit(final XYSeries series) {
        ticChartUnit = new ChartUnit(msrunName, reportIndex, series);
        setMaxIntensityMetric(Double.toString(ticChartUnit.getMaxTicIntensity()));
    }

    /**
     * Set the max intensity metric value.
     *
     * @param maxIntensityString the max intensity.
     */
    public void setMaxIntensityMetric(final String maxIntensityString) {
        setMetricValue(Constants.METRIC_KEY_MAX_INTENSITY, maxIntensityString);
    }

    /**
     * Get ticChart and corresponding chart data for this report unit.
     *
     * @return ChartUnit corresponding to this reportUnit.
     */
    public ChartUnit getChartUnit() {
        return ticChartUnit;
    }
    
    /**
     * Set the value of parameter fileSize.
     *
     * @param size size of the RAW MS data file (in MB).
     */
    public void setFileSizeString(final String size) {
        this.fileSizeString = size;
        final boolean valid = size != null && !size.equals(Constants.NOT_AVAILABLE_STRING) && !size.trim().isEmpty();
        this.fileSize = valid ? Double.parseDouble(size) : null;
        setMetricValue(Constants.METRIC_KEY_FILE_SIZE, size);
    }

    /**
     * Get the value of parameter ms1Spectra.
     *
     * @return number of ms1 spectra.
     */
    public String getMs1Spectra() {
        return this.ms1Spectra;
    }
    
    /**
     * Set the value of parameter ms1Spectra.
     *
     * @param ms1Spectra number of ms1spectra.
     */
    public void setMs1Spectra(final String ms1Spectra) {
        this.ms1Spectra = ms1Spectra;
        setMetricValue(Constants.METRIC_KEY_MS1_SPECTRA, ms1Spectra);
    }

    /**
     * Get the value of parameter ms2Spectra.
     *
     * @return number of ms2spectra.
     */
    public String getMs2Spectra() {
        return this.ms2Spectra;
    }

    /**
     * Set the value of parameter ms2Spectra.
     *
     * @param ms2Spectra number of ms2spectra.
     */
    public void setMs2Spectra(final String ms2Spectra) {
        this.ms2Spectra = ms2Spectra;
        setMetricValue(Constants.METRIC_KEY_MS2_SPECTRA, ms2Spectra);
    }

    /**
     * Get the value of parameter measured.
     *
     * @return day and time at which QC processing of the RAW MS data file begun.
     */
    public String getMeasured() {
        return this.measured;
    }

    /**
     * Set the value of parameter measured.
     *
     * @param measured day and time at which QC processing of the RAW MS data file begun.
     */
    public void setMeasured(final String measured) {
        this.measured = measured;
        setMetricValue(Constants.METRIC_KEY_MEASURED, measured);
    }

    /**
     * Get the value of parameter runtime.
     *
     * @return time (in hh:mm:ss) taken to complete the QC processing of RAW data file.
     */
    public String getRuntime() {
        return this.runtime;
    }

    /**
     * Set the value of parameter runtime.
     *
     * @param runtime time (in hh:mm:ss) taken to complete the QC processing of RAW data file.
     */
    public void setRuntime(final String runtime) {
        this.runtime = runtime;
        setMetricValue(Constants.METRIC_KEY_RUNTIME, runtime);
    }

    /**
     * Set values of QC metrics in this report.
     *
     * @param metricsValues map containing QC metrics keys and corresponding values.
     */
    public void setMetricsValues(final Map<String, String> metricsValues) {
        if (metricsValues != null) {
            this.metricsValues = new HashMap<>(metricsValues);
            // Set values of certain parameters to aid in the comparison.
            this.fileSizeString = this.getMetricsValueFromKey(Constants.METRIC_KEY_FILE_SIZE);
            setFileSizeString(fileSizeString);
            this.ms1Spectra = this.getMetricsValueFromKey(Constants.METRIC_KEY_MS1_SPECTRA);
            this.ms2Spectra = this.getMetricsValueFromKey(Constants.METRIC_KEY_MS2_SPECTRA);
            this.measured = this.getMetricsValueFromKey(Constants.METRIC_KEY_MEASURED);
            this.runtime = this.getMetricsValueFromKey(Constants.METRIC_KEY_RUNTIME);
        }
    }

    /**
     * Set an individual metric.
     *
     * @param key the metric key.
     * @param value the metric value.
     */
    public void setMetricValue(final String key, final String value) {
        if (metricsValues == null) {
            metricsValues = new HashMap<>();
        }
        metricsValues.put(key, value);
    }

    /**
     * Get map with values of QC metrics in this report.
     *
     * @return metricsValues map containing QC metrics keys and corresponding values.
     */
    public Map<String, String> getMetricsValues() {
        return metricsValues;
    }

    /**
     * Get a comparator to compare report units.
     *
     * TODO: should we store the metric type for all metrics to be able to sort correctly? [Freek]
     *
     * @param sortKey the key to sort on.
     * @param ascending whether to sort in ascending or descending order.
     * @return the comparator to compare report units.
     */
    public static Comparator<ReportUnit> getComparatorV2(final String sortKey, final boolean ascending) {
        return new ReportUnitComparator(sortKey, ascending);
    }


    // For debugging purposes:
//    public void printReportValues() {
//        logger.log(Level.ALL, "Num : " + this.reportNum + " fileSize = " + this.fileSizeString + " ms1Spectra = " +
//                              this.ms1Spectra + " ms2Spectra = " + this.ms2Spectra + " measured = " + measured +
//                              " runtime = " + runtime);
//    }


    // For debugging purposes:
    @Override
    public String toString() {
        return "Number: " + reportNum + ", file size: " + fileSizeString + ", ms1 spectra: " + ms1Spectra
               + ", ms2 spectra: " + ms2Spectra + ", measured: " + measured + ", runtime: " + runtime;
    }
}
