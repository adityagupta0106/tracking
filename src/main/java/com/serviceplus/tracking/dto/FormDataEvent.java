package com.serviceplus.tracking.dto;

import java.util.List;
import java.util.Map;

public class FormDataEvent {

    private String applicationId;
    private Integer serviceId;
    private String taskId;
    private String holderId;
    private String tenantId;
    private Map<String, Object> formData;
    private List<String> keys;
	public String getApplicationId() {
		return applicationId;
	}
	public void setApplicationId(String applicationId) {
		this.applicationId = applicationId;
	}
	public Integer getServiceId() {
		return serviceId;
	}
	public void setServiceId(Integer serviceId) {
		this.serviceId = serviceId;
	}
	public String getTaskId() {
		return taskId;
	}
	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}
	public String getHolderId() {
		return holderId;
	}
	public void setHolderId(String holderId) {
		this.holderId = holderId;
	}
	public String getTenantId() {
		return tenantId;
	}
	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}
	public Map<String, Object> getFormData() {
		return formData;
	}
	public void setFormData(Map<String, Object> formData) {
		this.formData = formData;
	}
	public List<String> getKeys() {
		return keys;
	}
	public void setKeys(List<String> keys) {
		this.keys = keys;
	}
    

}
