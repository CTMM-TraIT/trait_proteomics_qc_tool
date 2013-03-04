package nl.ctmm.trait.proteomics.qcviewer.input;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import nl.ctmm.trait.proteomics.qcviewer.utils.Constants;

import org.jfree.data.xy.XYSeries;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * This class contains the logic to read the directory/file structure and prepare data to be displayed.
 *
 * @author <a href="mailto:pravin.pawar@nbic.nl">Pravin Pawar</a>
 * @author <a href="mailto:freek.de.bruijn@nbic.nl">Freek de Bruijn</a>
 */
public class ReportReader extends JFrame {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static final Logger logger = Logger.getLogger(ReportReader.class.getName());
    private static final List<String> MONTH_DIRS = Arrays.asList(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    );

    private int currentReportNum;
    private String serverAddress = "";

    /**
     * Search through the directories under the root directory for files generated by the QC tool and return the
     * relevant data.
     *
     * @param rootDirectoryName the root directory that contains the year directories.
     * @param serverAddress 
     * @param tillDate 
     * @param fromDate 
     * @return a list with report units.
     */
    public List<ReportUnit> retrieveReports(final String rootDirectoryName, String serverAddress, final Date fromDate, final Date tillDate) {
        // todo msrun versus msreading directory?
        /*The directory has three levels - year, month and msrun.
        The msreading directory may contain following three files of importance:
	    1) metrics.json: String file which has following format: 
	    {"generic": {"date": "2012/Nov/07 - 14:18", "ms2_spectra": 
	    ["MS2 Spectra", "22298 (22298)"], "runtime": "0:16:23", 
	    "f_size": ["File Size (MB)", "830.9"], "ms1_spectra": 
	    ["MS1 Spectra", "7707 (7707)"]}}
	    2) msrun*_heatmap.png
	    3) msrun*_ions.png
	    */
    	String allErrorMessages = ""; 
    	this.serverAddress = serverAddress;
        final List<ReportUnit> reportUnits = new ArrayList<ReportUnit>();
        logger.log(Level.ALL, "Root folder = " + rootDirectoryName);
        for (final File yearDirectory : getYearDirectories(rootDirectoryName)) {
            logger.fine("Year = " + yearDirectory.getName());
            for (final File monthDirectory : getMonthDirectories(yearDirectory)) {
                logger.fine("Month = " + monthDirectory.getName());
                for (final File msRunDirectory : getMsRunDirectories(monthDirectory)) {
                    logger.fine("Msrun = " + msRunDirectory.getName());
                    long datetime = msRunDirectory.lastModified();
                    Date d = new Date(datetime);
                    SimpleDateFormat sdf = new SimpleDateFormat(Constants.SIMPLE_DATE_FORMAT_STRING);
                    String dateString = sdf.format(d);
                    if (d.compareTo(fromDate)>=0 && d.compareTo(tillDate)<=0) {
                    	System.out.println("Added - The folder was last modified on: " + dateString + " is within limits From " 
                    			+ sdf.format(fromDate) + " Till " + sdf.format(tillDate));
                    
                    	final File[] dataFiles = msRunDirectory.listFiles();
                    	//Check existence of "metrics.json", "heatmap.png", "ions.png", "_ticmatrix.csv"
                    	String errorMessage = checkDataFilesAvailability(yearDirectory.getName(), monthDirectory.getName(), msRunDirectory.getName(), dataFiles);
                    	if (!errorMessage.equals("")) {
                    		System.out.println("ErrorMessage = " + errorMessage);
                    		allErrorMessages += errorMessage + "\n";
                    	}
                    	reportUnits.add(createReportUnit(yearDirectory.getName(), monthDirectory.getName(), msRunDirectory.getName(), dataFiles));
                    } else System.out.println("Skipped - The folder was last modified on: " + dateString + " is outside limits From " 
                			+ sdf.format(fromDate) + " Till " + sdf.format(tillDate));
                }
            }
        }
        if (!allErrorMessages.equals("")) {
        	saveErrorMessages(allErrorMessages);
        	JOptionPane.showMessageDialog(this, allErrorMessages, "Errors",JOptionPane.ERROR_MESSAGE);
        }
        return reportUnits;
    }

    /**
     * Check existence of "metrics.json", "heatmap.png", "ions.png", "_ticmatrix.csv"
     */
    private String checkDataFilesAvailability(final String year, final String month, final String msrunName, final File[] dataFiles) {
    	String errorMessage = "";
    	boolean metrics = false, heatmap = false, ionCount = false, ticMatrix = false, overall = false;
        for (final File dataFile : dataFiles) {
            final String dataFileName = dataFile.getName();
            if (dataFile.isFile()) {
                logger.fine("File " + dataFileName);
                if (dataFileName.equals("metrics.json")) {
                   	metrics = true;
                } else if (dataFileName.endsWith("heatmap.png")) {
                    heatmap = true;
                } else if (dataFileName.endsWith("ions.png")) {
                    ionCount = true;
                } else if (dataFileName.endsWith("_ticmatrix.csv")) {
                    ticMatrix = true;
                }
            }
        }
        if (metrics && heatmap && ionCount && ticMatrix) {
        	overall = true;
        } else {
        	errorMessage = "<html>In Folder " + msrunName + " following filetypes are missing:";
        	if (!metrics) {
        		errorMessage += "metrics.json ";
        	}
        	if (!heatmap) {
        		errorMessage += "heatmap.png ";
        	}
        	if (!ionCount) {
        		errorMessage += "ions.png ";
        	}
        	if (!ticMatrix) {
        		errorMessage += "_ticmatrix.csv ";
        	}
        	errorMessage += "</html>";
        }
        return errorMessage;
    }
    
    private void saveErrorMessages(String allErrorMessages) {
    	try {
        	//Save errorMessages to errorMEssages.txt file
            FileWriter fWriter = new FileWriter("QCReports\\errorMessages.txt", true);
            BufferedWriter bWriter = new BufferedWriter(fWriter);
    		Date date = new Date();
    		bWriter.write(date.toString() + "\n");
			bWriter.write(allErrorMessages + "\n");
			bWriter.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    
    /**
     * Retrieve the year directories in the root directory.
     *
     * @param rootDirectoryName the name of the root directory in which to search for year directories.
     * @return the list of year directories.
     */
    private List<File> getYearDirectories(final String rootDirectoryName) {
        final List<File> yearDirectories = new ArrayList<File>();
        final File rootDirectory = new File(rootDirectoryName);
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
            logger.fine("File " + yearFileName);
        } else if (yearFile.isDirectory()) {
            logger.fine("Directory " + yearFileName);
            // Confirm whether yearFileName is a 4 digit number or not.
            if (Pattern.compile("[0-9][0-9][0-9][0-9]").matcher(yearFileName).matches()) {
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
        final List<File> monthDirectories = new ArrayList<File>();
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
        final ArrayList<File> msRunDirectories = new ArrayList<File>();
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
     * We use three specific files:
     * 1) metrics.json: String file which has following format:
     * {"generic": {"date": "2012/Nov/07 - 14:18", "ms2_spectra": ["MS2 Spectra", "22298 (22298)"],
     * "runtime": "0:16:23", "f_size": ["File Size (MB)", "830.9"],
     * "ms1_spectra": ["MS1 Spectra", "7707 (7707)"]}}
     * 2) msrun*_heatmap.png
     * 3) msrun*_ions.png
     *
     * @param dataFiles the files used to initialize the report unit.
     * @return the new report unit.
     */
    private ReportUnit createReportUnit(final String year, final String month, final String msrunName, final File[] dataFiles) {
        currentReportNum++;
        final ReportUnit reportUnit = new ReportUnit(msrunName, currentReportNum);
        for (final File dataFile : dataFiles) {
            final String dataFileName = dataFile.getName();
            if (dataFile.isFile()) {
                logger.fine("File " + dataFileName);
                try {
                    if (dataFileName.equals("metrics.json")) {
                        readJsonValues(dataFile, reportUnit);
                    } else if (dataFileName.endsWith("heatmap.png")) {
                        // todo equals instead of endsWith?
                        reportUnit.setHeatmap(ImageIO.read(dataFile), dataFileName);
                    } else if (dataFileName.endsWith("ions.png")) {
                        // todo equals instead of endsWith?
                        reportUnit.setIoncount(ImageIO.read(dataFile), dataFileName);
                    } else if (dataFileName.endsWith("_ticmatrix.csv")) {
                        // todo equals instead of endsWith?
                        reportUnit.createChartUnit(readXYSeries(msrunName, dataFile));
                    }
                } catch (IOException e) {
                    System.out.println(e.toString());
                    // todo handle this exception: preferably show what has gone wrong.
                    //todo add support for error dialogues
                }
            } else if (dataFile.isDirectory()) {
                logger.fine("Directory " + dataFileName);
            }
        }
        //Set the URI
        String detailsUri = "http://" + this.serverAddress + Constants.SERVER_LINK_POSTAMBLE + "/" + year + "/" + month + "/" + msrunName + "/";
        reportUnit.setDetailsUri(detailsUri);
        return reportUnit;
    }


    /**
     * Read the QC parameters from the json file.
     *
     * @param jsonFile   the json file that contains the QC parameters.
     * @param reportUnit the report unit where the QC parameters will be stored.
     */
    private void readJsonValues(final File jsonFile, final ReportUnit reportUnit) {
        logger.fine("IN readJsonValues - reading file " + jsonFile.getName());
        try {
            final JSONObject jsonObject = (JSONObject) new JSONParser().parse(new FileReader(jsonFile));
            final JSONObject genericObject = (JSONObject) jsonObject.get("generic");
            reportUnit.setFileSizeString((String) ((JSONArray) genericObject.get("f_size")).get(1));
            reportUnit.setMs1Spectra((String) ((JSONArray) genericObject.get("ms1_spectra")).get(1));
            reportUnit.setMs2Spectra((String) ((JSONArray) genericObject.get("ms2_spectra")).get(1));
            reportUnit.setMeasured((String) genericObject.get("date"));
            reportUnit.setRuntime((String) genericObject.get("runtime"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Create XYSeries by reading TIC matrix file that contains X & Y axis values representing TIC graph
     * @param msrunName
     * @param ticMatrixFile
     * @return XYSeries
     */
    private XYSeries readXYSeries(final String msrunName, final File ticMatrixFile) {
        BufferedReader br = null;
    	try {
    		br = new BufferedReader(new FileReader(ticMatrixFile));
    	} catch (FileNotFoundException e) {
    		e.printStackTrace();
    	}
        String line;
        XYSeries series = new XYSeries(msrunName);
        try {
            br.readLine(); //skip first line
    		while ((line = br.readLine()) != null) {
    		    StringTokenizer st = new StringTokenizer(line, ",");
    		    // The first token is the x value.
    		    String xValue = st.nextToken();
    		    // The last token is the y value.
    		    String yValue = st.nextToken();
    		    float x = Float.parseFloat(xValue)/60;
    		    float y = Float.parseFloat(yValue);
    		    series.add(x, y);
    		    //System.out.println("xValue = " + xValue + " x = " + x);
    		}
    	    br.close();
    	} catch (NumberFormatException | IOException e) {
    		e.printStackTrace();
    	}
        return series;
    }
}
