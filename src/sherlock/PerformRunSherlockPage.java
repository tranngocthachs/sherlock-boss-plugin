package sherlock;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import javax.servlet.ServletException;

import org.apache.log4j.Level;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;

import uk.ac.warwick.dcs.boss.frontend.PageContext;
import uk.ac.warwick.dcs.boss.frontend.sites.StaffPageFactory;
import uk.ac.warwick.dcs.boss.model.FactoryException;
import uk.ac.warwick.dcs.boss.model.FactoryRegistrar;
import uk.ac.warwick.dcs.boss.model.dao.DAOException;
import uk.ac.warwick.dcs.boss.model.dao.DAOFactory;
import uk.ac.warwick.dcs.boss.model.dao.IAssignmentDAO;
import uk.ac.warwick.dcs.boss.model.dao.IDAOSession;
import uk.ac.warwick.dcs.boss.model.dao.IModuleDAO;
import uk.ac.warwick.dcs.boss.model.dao.IResourceDAO;
import uk.ac.warwick.dcs.boss.model.dao.IStaffInterfaceQueriesDAO;
import uk.ac.warwick.dcs.boss.model.dao.ISubmissionDAO;
import uk.ac.warwick.dcs.boss.model.dao.beans.Assignment;
import uk.ac.warwick.dcs.boss.model.dao.beans.Module;
import uk.ac.warwick.dcs.boss.model.dao.beans.Submission;
import uk.ac.warwick.dcs.boss.plugins.spi.pages.StaffPluginPageProvider;
import uk.ac.warwick.dcs.cobalt.sherlock.DirectoryFilter;
import uk.ac.warwick.dcs.cobalt.sherlock.DynamicTreeTableModel;
import uk.ac.warwick.dcs.cobalt.sherlock.FileTypeProfile;
import uk.ac.warwick.dcs.cobalt.sherlock.GzipFilenameFilter;
import uk.ac.warwick.dcs.cobalt.sherlock.GzipHandler;
import uk.ac.warwick.dcs.cobalt.sherlock.Marking;
import uk.ac.warwick.dcs.cobalt.sherlock.MatchTableDataStruct;
import uk.ac.warwick.dcs.cobalt.sherlock.MatchTreeNodeStruct;
import uk.ac.warwick.dcs.cobalt.sherlock.Samelines;
import uk.ac.warwick.dcs.cobalt.sherlock.Settings;
import uk.ac.warwick.dcs.cobalt.sherlock.SherlockProcess;
import uk.ac.warwick.dcs.cobalt.sherlock.SherlockProcessCallback;
import uk.ac.warwick.dcs.cobalt.sherlock.SherlockProcessException;
import uk.ac.warwick.dcs.cobalt.sherlock.TextFileFilter;
import uk.ac.warwick.dcs.cobalt.sherlock.TokeniseFiles;
import uk.ac.warwick.dcs.cobalt.sherlock.ZipFilenameFilter;
import uk.ac.warwick.dcs.cobalt.sherlock.ZipHandler;

public class PerformRunSherlockPage extends StaffPluginPageProvider implements
SherlockProcessCallback {
	public String getName() {
		return "perform_run_sherlock";
	}

	public String getPageTemplate() {
		return "staff_run_sherlock_result";
	}
	/**
	 * The process that is to be run TokeniseFiles or Samelines.
	 */
	private SherlockProcess process = null;

	public void handleGet(PageContext pageContext, Template template,
			VelocityContext templateContext) throws ServletException,
			IOException {
		IDAOSession f;
		try {
			DAOFactory df = (DAOFactory) FactoryRegistrar
			.getFactory(DAOFactory.class);
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

		String doString = pageContext.getParameter("do");
		if (doString == null || !doString.equals("View Result"))
			throw new ServletException("Unexpected GET request");
		
		// Get session Id
		String sessionString = pageContext.getParameter("session");
		if (sessionString == null) {
			throw new ServletException("No session parameter given");
		}
		Long sessionId = Long
		.valueOf(pageContext.getParameter("session"));
		
		
		// Housekeeping stuff for the page
		try {
			f.beginTransaction();

			IStaffInterfaceQueriesDAO staffInterfaceQueriesDao = f
			.getStaffInterfaceQueriesDAOInstance();
			IAssignmentDAO assignmentDao = f.getAssignmentDAOInstance();
			Assignment assignment = assignmentDao
			.retrievePersistentEntity(assignmentId);

			if (!staffInterfaceQueriesDao.isStaffModuleAccessAllowed(
					pageContext.getSession().getPersonBinding().getId(),
					assignment.getModuleId())) {
				f.abortTransaction();
				throw new ServletException("permission denied");
			}

			IModuleDAO moduleDao = f.getModuleDAOInstance();
			Module module = moduleDao.retrievePersistentEntity(assignment
					.getModuleId());
			String[] selectedFiles = f.getAdditionalDAOInstance(SherlockSession.class).retrievePersistentEntity(sessionId).getSelectedFilenames();
			templateContext.put("greet", pageContext.getSession()
					.getPersonBinding().getChosenName());
			templateContext.put("module", module);
			templateContext.put("assignment", assignment);
			templateContext.put("files", selectedFiles);

			f.endTransaction();
		} catch (DAOException e) {
			f.abortTransaction();
			throw new ServletException("dao exception");
		}
		
		// loading the matching result and setting up the template context
		// Preparing matching results
		// loading saved marking [suspicious] items
		Marking marking = null;
		File savedMarkingFile = new File(Settings.getSourceDirectory(), "sherlockMarking.txt");
		if (savedMarkingFile.exists()) {
			marking = new Marking();
			marking.setMatches(MatchTableDataStruct.loadMatches());
			marking.load(savedMarkingFile);
			marking.generate();
		}
		MatchTableDataStruct mtd = new MatchTableDataStruct(marking);
		boolean hasMatch = mtd.hasMatch();
		templateContext.put("hasMatch", hasMatch);
		if (hasMatch) {
			DynamicTreeTableModel tm = mtd.getModel();
			Collection<String> thead = new ArrayList<String>(tm
					.getColumnCount());
			for (int i = 0; i < tm.getColumnCount(); i++)
				thead.add(tm.getColumnName(i));
			templateContext.put("tableHead", thead);
			List<String> nodeIds = new ArrayList<String>();
			List<List<String>> rows = new ArrayList<List<String>>();
			List<String> classes = new ArrayList<String>();
			List<Integer> matchIndices = new ArrayList<Integer>();
			Object root = tm.getRoot();
			walk(tm, root, "node", nodeIds, classes, rows, matchIndices);
			templateContext.put("ids", nodeIds);
			templateContext.put("classes", classes);
			templateContext.put("rows", rows);
			templateContext.put("matchIndices", matchIndices);
		}
		
		templateContext.put("viewResult", true);
		templateContext.put("session", sessionId);

		pageContext.renderTemplate(template, templateContext);
	}

	public void handlePost(PageContext pageContext, Template template,
			VelocityContext templateContext) throws ServletException,
			IOException {
		IDAOSession f;
		try {
			DAOFactory df = (DAOFactory) FactoryRegistrar
			.getFactory(DAOFactory.class);
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

		String doString = pageContext.getParameter("do");
		if (doString == null)
			throw new ServletException("Unexpected POST request");
		boolean newSession = false;
		boolean rerun = false;
		Long sherlockSessionId = new Long(-1);
		if (doString.equals("newSession"))
			newSession = true;
		else if (doString.equals("rerun")) {
			rerun = true;
			String sherlockSessionIdString = pageContext
			.getParameter("session");
			if (sherlockSessionIdString != null)
				sherlockSessionId = Long.valueOf(pageContext
						.getParameter("session"));
			else
				throw new ServletException("Unexpected POST request");
		}

		Collection<String> selectedFiles = null;
		if (newSession) {
			// Get files to run
			String[] files = pageContext.getParameterValues("files");
			if (files == null || files.length == 0) {
				pageContext.performRedirect(pageContext.getPageUrl(
						StaffPageFactory.SITE_NAME,
						"run_sherlock")
						+ "?assignment=" + assignmentId + "&missing=true&do=New+Session");
				return;
			}
			selectedFiles = Arrays.asList(files);
		} else if (rerun) {
			try {
				f.beginTransaction();
				selectedFiles = Arrays.asList(f.getAdditionalDAOInstance(SherlockSession.class).retrievePersistentEntity(sherlockSessionId).getSelectedFilenames());
				f.endTransaction();
			} catch (DAOException e) {
				f.abortTransaction();
				throw new ServletException("dao exception");
			}
		}

		

		// Validate submitted settings
		boolean validSettings = true;
		FileTypeProfile[] fileTypes = Settings.getFileTypes();
		int numFTSelected = 0;
		for (int i = 0; i < fileTypes.length; i++) {
			// a file type profile is identified by its extension
			// (according to the way the setting page has been generated)
			String type = fileTypes[i].getExtension();
			String selected = pageContext.getParameter(type);
			if (selected != null && selected.equals("true")) {
				numFTSelected++;
				// all the text inputs represent parameters whose each value
				// should be a number between 0 and 9
				// (with one exception of similarity threshold in sentence
				// profile where the number should be
				// between 0 and 99)
				if (i != Settings.SEN) {
					for (int j = 0; j < 6; j++) {
						int value = -1;
						try {
							value = Integer.parseInt(pageContext
									.getParameter(type + "-" + j));
						} catch (Exception e) {
							validSettings = false;
							break;
						}
						if (value < 0 || value > 9) {
							validSettings = false;
							break;
						}
					}
				} else {
					int value = -1;
					// first value of sentence profile setting is similarity
					// threshold
					// should be a number between 0 and 99
					try {
						value = Integer.parseInt(pageContext.getParameter(type
								+ "-" + 0));
					} catch (Exception e) {
						validSettings = false;
						break;
					}
					if (value < 0 || value > 99) {
						validSettings = false;
						break;
					}

					// second value
					value = -1;
					try {
						value = Integer.parseInt(pageContext.getParameter(type
								+ "-" + 1));
					} catch (Exception e) {
						validSettings = false;
						break;
					}
					if (value < 0 || value > 9) {
						validSettings = false;
						break;
					}
				}
			}
			if (!validSettings)
				break;
		}
		if (numFTSelected == 0)
			validSettings = false;
		if (!validSettings) {
			String url = pageContext.getPageUrl(
					StaffPageFactory.SITE_NAME,
					"run_sherlock")
					+ "?assignment=" + assignmentId + "&missing=true&do=";
			if (newSession)
				url += "New+Session";
			else if (rerun)
				url += ("Rerun&session=" + sherlockSessionId);
			pageContext.performRedirect(url);
			return;
		}

		// Render page
		try {
			f.beginTransaction();

			IStaffInterfaceQueriesDAO staffInterfaceQueriesDao = f
			.getStaffInterfaceQueriesDAOInstance();
			IAssignmentDAO assignmentDao = f.getAssignmentDAOInstance();
			Assignment assignment = assignmentDao
			.retrievePersistentEntity(assignmentId);

			if (!staffInterfaceQueriesDao.isStaffModuleAccessAllowed(
					pageContext.getSession().getPersonBinding().getId(),
					assignment.getModuleId())) {
				f.abortTransaction();
				throw new ServletException("permission denied");
			}

			IModuleDAO moduleDao = f.getModuleDAOInstance();
			Module module = moduleDao.retrievePersistentEntity(assignment
					.getModuleId());

			templateContext.put("greet", pageContext.getSession()
					.getPersonBinding().getChosenName());
			templateContext.put("module", module);
			templateContext.put("assignment", assignment);
			templateContext.put("files", selectedFiles);

			f.endTransaction();
		} catch (DAOException e) {
			f.abortTransaction();
			throw new ServletException("dao exception");
		}

		File sherlockTempDir = Settings.getSourceDirectory();
		if (newSession) {
			try {
				f.beginTransaction();
				// Obtain submissions
				ISubmissionDAO submissionDao = f.getSubmissionDAOInstance();
				Submission exampleSubmission = new Submission();
				exampleSubmission.setAssignmentId(assignmentId);
				Collection<Submission> submissionsToProcess = submissionDao
				.findPersistentEntitiesByExample(exampleSubmission);
				pageContext.log(Level.DEBUG, "Extracting "
						+ submissionsToProcess.size() + " submission(s)");
				IResourceDAO resourceDao = f.getResourceDAOInstance();

				for (Submission submission : submissionsToProcess) {
					InputStream resourceStream = resourceDao
					.openInputStream(submission.getResourceId());
					ZipInputStream zipResourceStream = new ZipInputStream(
							resourceStream);
					ZipEntry currentZipEntry;
					byte buffer[] = new byte[1024];
					FileOutputStream fos = null;
					try {
						while ((currentZipEntry = zipResourceStream
								.getNextEntry()) != null) {
							String destination = sherlockTempDir
							.getAbsolutePath()
							+ File.separator
							+ currentZipEntry.getName();
							File destinationFile = new File(destination);

							if (!currentZipEntry.isDirectory()
									&& !destinationFile.getParentFile()
									.exists()) {
								destinationFile.getParentFile().mkdirs();
							}

							if (currentZipEntry.isDirectory()
									&& !destinationFile.exists()) {
								destinationFile.getParentFile().mkdirs();
							}

							if (selectedFiles.contains(destinationFile
									.getName())) {
								fos = new FileOutputStream(destinationFile);
								int n;
								while ((n = zipResourceStream.read(buffer, 0,
										1024)) > -1) {
									fos.write(buffer, 0, n);
								}

								fos.flush();
								fos.close();
							}
						}
					} catch (IOException e) {
						if (fos != null)
							fos.close();
						if (zipResourceStream != null)
							zipResourceStream.close();
						throw new ServletException(
								"IO Error while extracting resource of submission: "
								+ submission.getId(), e);
					}
				}
				f.endTransaction();
			} catch (DAOException e) {
				f.abortTransaction();
				throw new ServletException("dao exception");
			}
		}

		
		// running Sherlock on temp folder
		if (rerun)
			// no need for preprocessing/renaming the file if it's a rerun because it should have been done the first time around
			runSherlock(sherlockTempDir, pageContext, false);
		else
			runSherlock(sherlockTempDir, pageContext, true);

		// wait for tokenising and detection to finish
		try {
			process.join();
		} catch (InterruptedException e) {
			Settings.message("Error tokenising/detecting files:\n"
					+ e.getMessage());
			throw new ServletException("Sherlock error; check log for details",
					e);
		}

		// Preparing matching results
		MatchTableDataStruct mtd = new MatchTableDataStruct(null);
		boolean hasMatch = mtd.hasMatch();
		templateContext.put("hasMatch", hasMatch);
		if (hasMatch) {
			DynamicTreeTableModel tm = mtd.getModel();
			Collection<String> thead = new ArrayList<String>(tm
					.getColumnCount());
			for (int i = 0; i < tm.getColumnCount(); i++)
				thead.add(tm.getColumnName(i));
			templateContext.put("tableHead", thead);
			List<String> nodeIds = new ArrayList<String>();
			List<List<String>> rows = new ArrayList<List<String>>();
			List<String> classes = new ArrayList<String>();
			List<Integer> matchIndices = new ArrayList<Integer>();
			Object root = tm.getRoot();
			walk(tm, root, "node", nodeIds, classes, rows, matchIndices);
			templateContext.put("ids", nodeIds);
			templateContext.put("classes", classes);
			templateContext.put("rows", rows);
			templateContext.put("matchIndices", matchIndices);
		}
		templateContext.put("newSession", newSession);
		templateContext.put("rerun", rerun);
		templateContext.put("session", sherlockSessionId);
		pageContext.renderTemplate(template, templateContext);
	}

	private void runSherlock(File srcDir, PageContext pageContext, boolean preprocessFileList)
	throws ServletException {

		Settings.setRunningGUI(false);

		FileTypeProfile[] fileTypes = Settings.getFileTypes();
		for (int i = 0; i < fileTypes.length; i++) {
			FileTypeProfile profile = fileTypes[i];

			// a file type profile is identified by its extension
			// (according to the way the setting page has been generated)
			String type = profile.getExtension();
			String selected = pageContext.getParameter(type);
			if (selected != null && selected.equals("true")) {
				profile.setInUse(true);
				if (i != Settings.SEN) {
					String[] values = new String[8];
					for (int j = 0; j < 8; j++) {
						values[j] = pageContext.getParameter(type + "-" + j);
					}
					profile.setMinStringLength(Integer.parseInt(values[0]));
					profile.setMinRunLength(Integer.parseInt(values[1]));
					profile.setMaxForwardJump(Integer.parseInt(values[2]));
					profile.setMaxBackwardJump(Integer.parseInt(values[3]));
					profile.setMaxJumpDiff(Integer.parseInt(values[4]));
					profile.setStrictness(Integer.parseInt(values[5]));
					profile.setAmalgamate((values[6] != null && values[6]
					                                                   .equals("true")) ? true : false);
					profile.setConcatanate((values[7] != null && values[7]
					                                                    .equals("true")) ? true : false);
				} else {
					profile.setSimThreshold(Integer.parseInt(pageContext
							.getParameter(type + "-0")));
					profile.setCommonThreshold(Integer.parseInt(pageContext
							.getParameter(type + "-1")));
					String memIntenVal = pageContext.getParameter(type + "-2");
					profile.setMemIntensive((memIntenVal != null && memIntenVal
							.equals("true")) ? true : false);
				}
			} else {
				profile.setInUse(false);
			}
		}

		if (preprocessFileList) {
			// preprocess file list
			Settings.setFileList(processDirectory(Settings.getSourceDirectory()));
			Settings.setFileList(renameFiles(Settings.getFileList()));
		}
		
		// Actually run Sherlock

		// Tokenise files
		process = new TokeniseFiles(this);
		process.start();

		// Detect copying!
		// Wait for tokenising to finish
		try {
			process.join();
		} catch (InterruptedException e) {
			Settings.message("Error tokenising files:\n" + e.getMessage());
			throw new ServletException("Sherlock error; check log for details",
					e);
		}
		process = new Samelines(this);
		process.start();

		// Save the settings used.
		for (int x = 0; x < Settings.NUMBEROFFILETYPES; x++) {
			Settings.getFileTypes()[x].store();
		}
		Settings.getSherlockSettings().store();
	}

	public void exceptionThrown(SherlockProcessException spe) {
		// Remove everything that's been done, and let the user know.
		process.deleteWorkDone();
		process.letProcessDie();

		String msg = spe.getMessage() + "\n"
		+ spe.getOriginalException().toString();

		Settings.message("Error:\n" + msg);
	}

	private static void walk(DynamicTreeTableModel model, Object o,
			String currentId, List<String> ids, List<String> classes,
			List<List<String>> rows, List<Integer> matchIndices) {
		int cc;
		cc = model.getChildCount(o);
		for (int i = 0; i < cc; i++) {
			Object child = model.getChild(o, i);
			String childId = currentId + "-" + i;
			ids.add(childId);
			List<String> row = recordNode(model, child);
			rows.add(row);
			matchIndices.add(new Integer(((MatchTreeNodeStruct) child)
					.getIndex()));
			// this is for discarding root node
			if (!currentId.equals("node"))
				classes.add("child-of-" + currentId);
			else
				classes.add("");
			if (!model.isLeaf(child))
				walk(model, child, childId, ids, classes, rows, matchIndices);
		}
	}

	private static List<String> recordNode(DynamicTreeTableModel model, Object o) {
		List<String> row = new ArrayList<String>(model.getColumnCount());
		for (int i = 0; i < model.getColumnCount(); i++) {
			row.add(model.getValueAt(o, i).toString());
		}
		return row;
	}

	/*
	 * Unpack ZIP/GZIP files within each sub-directories in the given source
	 * directory.
	 */
	private static File[] processDirectory(File dir) {
		DirectoryFilter dirfilter = new DirectoryFilter();
		ZipFilenameFilter zipfilter = new ZipFilenameFilter();
		GzipFilenameFilter gzipfilter = new GzipFilenameFilter();
		TextFileFilter textfilefilter = new TextFileFilter();

		// for each sub-directory, expand any zip/gzip files in it and its
		// sub-directories if any.
		File[] subdir;
		File[] zipfiles = dir.listFiles(zipfilter);
		File[] gzipfiles = dir.listFiles(gzipfilter);
		File[] list;
		File[] subdirfiles;
		LinkedList<File> l = new LinkedList<File>();

		// add files in current directory
		File[] files = dir.listFiles(textfilefilter);
		for (int i = 0; i < files.length; i++) {
			l.add(files[i]);

			// for each zip file in this directory if any
		}
		for (int i = 0; i < zipfiles.length; i++) {
			try {
				ZipHandler.unzip(new ZipFile(zipfiles[i]));
			} catch (IOException e1) {
				// write error log, skip this file and continue.
				Date day = new Date(System.currentTimeMillis());
				try {
					BufferedWriter out = new BufferedWriter(new FileWriter(
							Settings.getLogFile().getAbsolutePath(), true));
					out.write(day + "-Cannont extract file: "
							+ zipfiles[i].getAbsolutePath() + " File skipped.");
					out.newLine();
					out.close();
				} catch (IOException e2) {
					// if failed to write to log, write to stderr
					System.err.println(day + "-Cannot write to log file. "
							+ "Cannont extract file: "
							+ zipfiles[i].getAbsolutePath() + " File skipped.");
				}
				continue;
			}
		}

		// for each gzip file in this directory if any
		for (int i = 0; i < gzipfiles.length; i++) {
			try {
				GzipHandler.gunzip(gzipfiles[i]);
			} catch (IOException e1) {
				// write error log, skip this file and continue.
				Date day = new Date(System.currentTimeMillis());
				try {
					BufferedWriter out = new BufferedWriter(new FileWriter(
							Settings.getLogFile().getAbsolutePath(), true));
					out
					.write(day + "-Cannont extract file: "
							+ gzipfiles[i].getAbsolutePath()
							+ " File skipped.");
					out.newLine();
					out.close();
				} catch (IOException e2) {
					// if failed to write to log file, write to stderr
					System.err
					.println(day + "-Cannot write to log file. "
							+ "Cannont extract file: "
							+ gzipfiles[i].getAbsolutePath()
							+ " File skipped.");
				}
				continue;
			}
		}

		// for each sub-directory in this directory if any
		subdir = dir.listFiles(dirfilter);
		int count = Settings.filterSherlockDirs(subdir);
		File[] newDirs = new File[count];
		int pos = 0;
		for (int i = 0; i < subdir.length; i++) {
			if (subdir[i] != null) {
				newDirs[pos] = subdir[i];
				pos++;
			}
		}
		subdir = newDirs;
		for (int i = 0; i < subdir.length; i++) {
			subdirfiles = processDirectory(subdir[i]);
			for (int j = 0; j < subdirfiles.length; j++) {
				l.add(subdirfiles[j]);
			}
		}

		// store result in a File array and return.
		list = new File[l.size()];
		for (int i = 0; i < l.size(); i++) {
			list[i] = (File) l.get(i);
		}
		return list;
	}

	/**
	 * Rename files, concatenates filenames with their parent directories'
	 * names.i.e. 9945423/a.java to 9945423/9945423a.java this operation ensures
	 * that filenames are unique and it is strongely coupled with BOSS, as the
	 * parent directories' names are assumed to be named by the student ID
	 * numbers.
	 * 
	 *@param l
	 *            array of files to rename
	 */
	private static File[] renameFiles(File[] l) {
		File[] list = new File[l.length];
		for (int i = 0; i < l.length; i++) {
			File file = (File) l[i];
			File parent = file.getParentFile();
			File newfile = new File(parent + Settings.getFileSep()
					+ parent.getName() + file.getName());
			boolean successful = file.renameTo(newfile);

			if (successful) {
				list[i] = newfile;
			} else {
				list[i] = file;
			}
		}
		return list;
	}
}
