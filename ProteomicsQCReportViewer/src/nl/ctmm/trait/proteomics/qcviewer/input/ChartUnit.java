package nl.ctmm.trait.proteomics.qcviewer.input;

import java.awt.Color;
import java.awt.Font;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * The object of this class represents tic chart and corresponding plot, domainAxis, rangeAxis and 
 * renderer of single msreading.
 *
 * @author <a href="mailto:pravin.pawar@nbic.nl">Pravin Pawar</a>
 * @author <a href="mailto:freek.de.bruijn@nbic.nl">Freek de Bruijn</a>
 */

public class ChartUnit {
	private String chartName = "";
	private XYSeries graphDataSeries = null;
	private int reportNum; 
	private JFreeChart ticChart = null; 
	private NumberAxis domainAxis = null;
    private NumberAxis rangeAxis = null;
    private XYBarRenderer renderer = null;
	private XYPlot plot = null;
    private static final List<Color> GRAPH_COLORS = Arrays.asList(
           Color.BLUE, Color.DARK_GRAY, Color.GRAY, Color.MAGENTA, Color.ORANGE, 
           Color.PINK, Color.LIGHT_GRAY, Color.RED, Color.GREEN);
    
	public ChartUnit(final String msrunName, final int reportNum, final XYSeries series) {
		this.chartName = msrunName; 
		this.reportNum = reportNum;
		this.graphDataSeries = series;
		domainAxis = new NumberAxis(null);
	    rangeAxis = new NumberAxis(null);
	    renderer = new XYBarRenderer();
	    //Sets the percentage amount by which the bars are trimmed
	    renderer.setMargin(0.98); //Default renderer margin is 0.0
	    renderer.setDrawBarOutline(false); 
	    renderer.setShadowVisible(false);
	    Color currentColor = GRAPH_COLORS.get(reportNum%GRAPH_COLORS.size());
	    renderer.setSeriesPaint(0, currentColor);
	    renderer.setBasePaint(Color.WHITE);
	    XYBarRenderer.setDefaultShadowsVisible(false);
	    //Add support for toolTip
	    StandardXYToolTipGenerator toolTipGenerator = new StandardXYToolTipGenerator();
	    renderer.setBaseToolTipGenerator(toolTipGenerator);
		XYSeriesCollection xyDataset = new XYSeriesCollection(series);
		//Prepare chart using plot - this is the best option to control domain and range axes
	    plot = new XYPlot(xyDataset, domainAxis, rangeAxis, renderer);
	    rangeAxis.setNumberFormatOverride(new DecimalFormat("0E00"));
	    int style = Font.BOLD;
	    Font font = new Font ("Garamond", style , 11);
	    ticChart = new JFreeChart(msrunName, font, plot, false);
	    // performance
	    ticChart.setAntiAlias(false);
	}
	
	/**
	 * Get max intensity of TIC graph
	 * @return Max intensity of TIC graph
	 */
	public double getMaxTicIntensity() {
		return rangeAxis.getRange().getUpperBound();
	}
	
    /**
     * Set the value of parameter graphDataSeries
     * @param series
     */
    public void setGraphDataSeries(XYSeries series) {
    	this.graphDataSeries = series;
    }
    
    /**
     * Get corresponding tiChart
     * @return ticChart
     */
    public JFreeChart getTicChart() {
    	return ticChart;
    }
    
    /**
     * Get corresponding domainAxis
     * @return domainAxis
     */
    public NumberAxis getDomainAxis() {
    	return domainAxis;
    }
}