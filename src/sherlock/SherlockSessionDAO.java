package sherlock;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Vector;

import uk.ac.warwick.dcs.boss.model.dao.DAOException;
import uk.ac.warwick.dcs.boss.model.dao.beans.Assignment;
import uk.ac.warwick.dcs.boss.model.dao.beans.Resource;
import uk.ac.warwick.dcs.boss.plugins.dbschema.SQLTableSchema;
import uk.ac.warwick.dcs.boss.plugins.spi.dao.PluginEntityDAO;

public class SherlockSessionDAO extends PluginEntityDAO<SherlockSession> {

	private String mySQLSortingString = "id DESC";
	
	public SherlockSession createInstanceFromDatabaseValues(String tableName,
			ResultSet databaseValues) throws SQLException, DAOException {
		SherlockSession result = new SherlockSession();
		result.setAssignmentId(databaseValues.getLong(tableName + ".assignment_id"));
		result.setResourceId(databaseValues.getLong(tableName + ".resource_id"));
		String[] selectedFilenames = null;
		String selectedFilenameStr = databaseValues.getString(tableName
				+ ".selected_filenames");
		if (selectedFilenameStr != null) {
			selectedFilenames = selectedFilenameStr.split("\\s*:\\s*");
		}
		result.setSelectedFilenames(selectedFilenames);
		return result;
	}

	public Collection<String> getDatabaseFieldNames() {
		Vector<String> fieldNames = new Vector<String>();
		fieldNames.add("assignment_id");
		fieldNames.add("resource_id");
		fieldNames.add("selected_filenames");
		return fieldNames;
	}

	
	public Collection<Object> getDatabaseValues(SherlockSession entity) {
		Vector<Object> output = new Vector<Object>();
		output.add(entity.getAssignmentId());
		output.add(entity.getResourceId());
		String[] selectedFilenames = entity.getSelectedFilenames();
		Object selectedFilenamesObj = null;
		if (selectedFilenames != null) {
			StringBuffer libFilenameStr = new StringBuffer();
			for (int i = 0; i < selectedFilenames.length; i++) {
				libFilenameStr.append(selectedFilenames[i].trim());
				if (i < selectedFilenames.length - 1)
					libFilenameStr.append(":");
			}
			selectedFilenamesObj = libFilenameStr.toString();
		}
		output.add(selectedFilenamesObj);
		return output;
	}

	public String getMySQLSortingString() {
		return mySQLSortingString;
	}

	public String getTableName() {
		return "sherlocksess";
	}

	public SQLTableSchema getTableSchema() {
		SQLTableSchema tblSchema = new SQLTableSchema(getTableName());
		tblSchema.addIntColumn("assignment_id", true);
		tblSchema.addIntColumn("resource_id", true);
		tblSchema.addTextColumn("selected_filenames", true);
		tblSchema.setForeignKey("assignment_id", Assignment.class);
		tblSchema.setForeignKey("resource_id", Resource.class);
		return tblSchema;
	}
}
