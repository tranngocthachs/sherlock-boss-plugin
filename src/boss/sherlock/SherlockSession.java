package boss.sherlock;

import boss.plugins.spi.dao.IPluginEntity;

public class SherlockSession extends IPluginEntity {

	private Long assignmentId;
	private Long resourceId;
	private String[] selectedFilenames;
	/**
	 * @return the assignmentId
	 */
	public Long getAssignmentId() {
		return assignmentId;
	}
	/**
	 * @param assignmentId the assignmentId to set
	 */
	public void setAssignmentId(Long assignmentId) {
		this.assignmentId = assignmentId;
	}
	/**
	 * @return the resourceId
	 */
	public Long getResourceId() {
		return resourceId;
	}
	/**
	 * @param resourceId the resourceId to set
	 */
	public void setResourceId(Long resourceId) {
		this.resourceId = resourceId;
	}
	/**
	 * @return the selectedFiles
	 */
	public String[] getSelectedFilenames() {
		return selectedFilenames;
	}
	/**
	 * @param selectedFiles the selectedFiles to set
	 */
	public void setSelectedFilenames(String[] selectedFilenames) {
		this.selectedFilenames = selectedFilenames;
	}
}
