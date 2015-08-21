package org.jenkinsci.plugins.websphere.services.deployment;


public class Server implements Comparable<Server>{

    private String objectName;
    private String target;
    private boolean selected;
    private int index;
    
	public int getIndex() {
		return index;
	}
	public void setIndex(int index) {
		this.index = index;
	}
	public String getObjectName() {
		return objectName;
	}
	public void setObjectName(String objectName) {
		this.objectName = objectName;
	}
	public boolean isSelected() {
		return selected;
	}
	public void setSelected(boolean selected) {
		this.selected = selected;
	}
	public String getTarget() {
		return target;
	}
	public void setTarget(String target) {
		this.target = target;
	}
	@Override
	public int compareTo(Server other) {
		return getTarget().compareTo(other.getTarget());
	}	
}
