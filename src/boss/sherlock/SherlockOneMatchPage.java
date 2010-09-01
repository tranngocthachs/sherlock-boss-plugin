package boss.sherlock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;

import uk.ac.warwick.dcs.boss.frontend.PageContext;
import uk.ac.warwick.dcs.boss.model.FactoryException;
import uk.ac.warwick.dcs.boss.model.FactoryRegistrar;
import uk.ac.warwick.dcs.boss.model.dao.DAOException;
import uk.ac.warwick.dcs.boss.model.dao.DAOFactory;
import uk.ac.warwick.dcs.boss.model.dao.IAssignmentDAO;
import uk.ac.warwick.dcs.boss.model.dao.IDAOSession;
import uk.ac.warwick.dcs.boss.model.dao.IStaffInterfaceQueriesDAO;
import uk.ac.warwick.dcs.boss.model.dao.beans.Assignment;
import uk.ac.warwick.dcs.boss.plugins.spi.pages.IStaffPluginPage;
import uk.ac.warwick.dcs.cobalt.sherlock.Match;
import uk.ac.warwick.dcs.cobalt.sherlock.MatchTableDataStruct;
import uk.ac.warwick.dcs.cobalt.sherlock.Settings;

public class SherlockOneMatchPage extends IStaffPluginPage {
	public String getPageName() {
		return "sherlock_one_match";
	}

	public String getPageTemplate() {
		return "staff_sherlock_one_match";
	}
	
	public void handleGet(PageContext pageContext, Template template,
			VelocityContext templateContext) throws ServletException,
			IOException {
		IDAOSession f;
		try {
			DAOFactory df = (DAOFactory)FactoryRegistrar.getFactory(DAOFactory.class);
			f = df.getInstance();
		} catch (FactoryException e) {
			throw new ServletException("dao init error", e);
		}

		// Get assignmentId
		String assignmentString = pageContext.getParameter("assignment");
		if (assignmentString == null) {
			throw new ServletException("No assignment parameter given");
		}
		Long assignmentId = Long
		.valueOf(pageContext.getParameter("assignment"));

		// Get matchIdx
		String matchIdxString = pageContext.getParameter("matchIdx");
		if (matchIdxString == null) {
			throw new ServletException("No match index parameter given");
		}
		int matchIdx = Integer.parseInt(matchIdxString);

		Match[] matches = MatchTableDataStruct.loadMatches();
		if (matches == null)
			throw new ServletException("There's no match available");

		// Render page
		try {
			f.beginTransaction();

			IStaffInterfaceQueriesDAO staffInterfaceQueriesDao = f.getStaffInterfaceQueriesDAOInstance();
			IAssignmentDAO assignmentDao = f.getAssignmentDAOInstance();
			Assignment assignment = assignmentDao.retrievePersistentEntity(assignmentId);

			if (!staffInterfaceQueriesDao.isStaffModuleAccessAllowed(pageContext.getSession().getPersonBinding().getId(), assignment.getModuleId())) {
				f.abortTransaction();
				throw new ServletException("permission denied");
			}
			f.endTransaction();
			Match match = matches[matchIdx];
			
			// Left original file.
		    String filename = match.getFile1();
		    int slashindex = filename.lastIndexOf(Settings.getFileSep());
		    int dotindex = filename.lastIndexOf('.');
		    filename = filename.substring(slashindex + 1, dotindex);
		    File inputFile = new File(Settings.getSourceDirectory() + Settings.getFileSep()
		                              + Settings.getFileTypes()[Settings.ORI]
		                              .getDirectory(),
		                              filename + "." +
		                              Settings.getFileTypes()[Settings.ORI]
		                              .getExtension());
		    int start = match.getRun().getStartCoordinates()
		        .getOrigLineNoInFile1();
		    int end = match.getRun().getEndCoordinates().getOrigLineNoInFile1();
		    Settings.message("leftorig, " + inputFile.getName() + ", " +
		                     String.valueOf(start) + ", " + String.valueOf(end));
		    String[] leftOrigin = getMatchedCode(inputFile, start, end);
			
		    // Right original file.
		    filename = match.getFile2();
		    slashindex = filename.lastIndexOf(Settings.getFileSep());
		    dotindex = filename.lastIndexOf('.');
		    filename = filename.substring(slashindex + 1, dotindex);
		    inputFile = new File(Settings.getSourceDirectory() + Settings.getFileSep()
		                         + Settings.getFileTypes()[Settings.ORI]
		                         .getDirectory(),
		                         filename + "." +
		                         Settings.getFileTypes()[Settings.ORI].getExtension());
		    start = match.getRun().getStartCoordinates().getOrigLineNoInFile2();
		    end = match.getRun().getEndCoordinates().getOrigLineNoInFile2();
		    Settings.message("rightorig, " + inputFile.getName() + ", " +
		                     String.valueOf(start) + ", " + String.valueOf(end));
		    String[] rightOrigin = getMatchedCode(inputFile, start, end);
		    
		    // Left tokenised file.
		    inputFile = new File(Settings.getSourceDirectory(), match.getFile1());
		    start = match.getRun().getStartCoordinates().getLineNoInFile1();
		    end = match.getRun().getEndCoordinates().getLineNoInFile1();
		    Settings.message("left, " + inputFile.getName() + ", " +
		                     String.valueOf(start) + ", " + String.valueOf(end));
		    String[] leftTokenised = getMatchedCode(inputFile, start, end);

		    // Right tokenised file.
		    inputFile = new File(Settings.getSourceDirectory(), match.getFile2());
		    start = match.getRun().getStartCoordinates().getLineNoInFile2();
		    end = match.getRun().getEndCoordinates().getLineNoInFile2();
		    Settings.message("right, " + inputFile.getName() + ", " +
		                     String.valueOf(start) + ", " + String.valueOf(end));
		    String[] rightTokenised = getMatchedCode(inputFile, start, end);
		    
		    templateContext.put("leftori", leftOrigin[0]);
		    templateContext.put("leftori-alt", leftOrigin[1]);
		    templateContext.put("rightori", rightOrigin[0]);
		    templateContext.put("rightori-alt", rightOrigin[1]);
		    templateContext.put("left", leftTokenised[0]);
		    templateContext.put("left-alt", leftTokenised[1]);
		    templateContext.put("right", rightTokenised[0]);
		    templateContext.put("right-alt", rightTokenised[1]);
			
			pageContext.renderTemplate(template, templateContext);
		} catch (DAOException e) {
			f.abortTransaction();
			throw new ServletException("dao exception", e);
		}

	}

	public void handlePost(PageContext pageContext, Template template,
			VelocityContext templateContext) throws ServletException,
			IOException {
		throw new ServletException("Unexpected POST");
	}

	private String[] getMatchedCode(File inputFile, int startLine,
			int endLine) throws ServletException {
		int additionalLines = 3;
		
		// return 2 strings, first one is matched section only (with 3 lines before and after),
		// second is whole file
		StringBuffer matchedSection = new StringBuffer();
		StringBuffer wholeFile = new StringBuffer();
		// Keep track of the lines in the file - so we know when to display
		// or not.
		int lineNo = 1;

		// Used to read from the file.
		String inputString = "";

		try {
			BufferedReader readFromFile = new BufferedReader
			(new FileReader(inputFile));

			inputString = readFromFile.readLine();

			while (inputString != null) {

				// Read in all of the file. Only output the lines that aren't
				// #line xxx
				// However, make sure that update the line number count.
				if (inputString.startsWith("#line ")) {
					//lineNo = Integer.parseInt(inputString.substring(6));
					inputString = readFromFile.readLine();
					continue;
				}

				// If at start of matched suspicious section:
				if (lineNo == startLine) {
					matchedSection.append("*****BEGIN SUSPICIOUS SECTION*****\n");
					wholeFile.append("*****BEGIN SUSPICIOUS SECTION*****\n");
					matchedSection.append(inputString + "\n");
					wholeFile.append(inputString + "\n");
				}

				// If at end of matched suspicious section:
				else if (lineNo == endLine) {
					matchedSection.append(inputString + "\n");
					wholeFile.append(inputString + "\n");
					matchedSection.append("*****END SUSPICIOUS SECTION*****\n");
					wholeFile.append("*****END SUSPICIOUS SECTION*****\n");
				}

				// If within three lines of the
				// limits, or between matched section:
				else if ((lineNo > startLine && lineNo < endLine) ||
							(lineNo > (startLine - additionalLines) && lineNo < startLine) ||
							(lineNo > endLine && lineNo < (endLine + additionalLines))) {
					matchedSection.append(inputString + "\n");
					wholeFile.append(inputString + "\n");
				}
				else {
					wholeFile.append(inputString + "\n");
				}

				// Read the next line.
				inputString = readFromFile.readLine();
				lineNo++;
			} // while reading in the file.
		}
		catch (FileNotFoundException a) {
			throw new ServletException(inputFile.getAbsolutePath() + " does not exist");
		}
		catch (IOException f) {
			throw new ServletException("Error while reading " + inputFile.getAbsolutePath());
		}
		String[] retval = {matchedSection.toString(), wholeFile.toString()};
		return retval;
	} // loadPane
}
