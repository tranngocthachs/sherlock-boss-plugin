package sherlock;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.Vector;

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
import uk.ac.warwick.dcs.boss.model.dao.IEntityDAO;
import uk.ac.warwick.dcs.boss.model.dao.IModuleDAO;
import uk.ac.warwick.dcs.boss.model.dao.IStaffInterfaceQueriesDAO;
import uk.ac.warwick.dcs.boss.model.dao.beans.Assignment;
import uk.ac.warwick.dcs.boss.model.dao.beans.Module;
import uk.ac.warwick.dcs.boss.plugins.spi.extralinks.StaffAssignmentPluginEntryProvider;
import uk.ac.warwick.dcs.boss.plugins.spi.pages.StaffPluginPageProvider;

public class InitSherlockPage extends StaffPluginPageProvider implements StaffAssignmentPluginEntryProvider {
	
	public String getName() {
		return "init_sherlock";
	}

	public String getLinkLabel() {
		return "Run Plagiarism with Sherlock";
	}

	public String getEntryPageName() {
		return getName();
	}

	public String getAssignmentParaString() {
		return "assignment";
	}

	public String getPageTemplate() {
		return "staff_init_sherlock";
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
		String assignmentString = pageContext.getParameter(getAssignmentParaString());
		if (assignmentString == null) {
			throw new ServletException("No assignment parameter given");
		}
		Long assignmentId = Long
				.valueOf(pageContext.getParameter(getAssignmentParaString()));
		
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
			
			
			IModuleDAO moduleDao = f.getModuleDAOInstance();
			Module module = moduleDao.retrievePersistentEntity(assignment.getModuleId());
			templateContext.put("greet", pageContext.getSession().getPersonBinding().getChosenName());
			templateContext.put("module", module);
			templateContext.put("assignment", assignment);
			
			IEntityDAO<SherlockSession> sherlockSessionDao = f.getAdditionalDAOInstance(SherlockSession.class);
			SherlockSession exampleSherlockSession = new SherlockSession();
			exampleSherlockSession.setAssignmentId(assignmentId);
			Collection<SherlockSession> sherlockSessions = sherlockSessionDao.findPersistentEntitiesByExample(exampleSherlockSession);
			templateContext.put("sessions", sherlockSessions);
			Collection<Date> savedTime = new Vector<Date>();
			if (!sherlockSessions.isEmpty()) {
				for (SherlockSession sherlockSession : sherlockSessions) {
					Long resourceId = sherlockSession.getResourceId();
					savedTime.add(f.getResourceDAOInstance().retrievePersistentEntity(resourceId).getTimestamp());
				}
				templateContext.put("times", savedTime);
			}
			f.endTransaction();
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

}
