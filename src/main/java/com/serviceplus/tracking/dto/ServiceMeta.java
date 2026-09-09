package com.serviceplus.tracking.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServiceMeta {

	private Integer serviceId;
	private String serviceName;
	private String formId;
	private String taskId;
    private String taskType;
    private Integer baseServiceId;
    private String currentProcessId;

    private String departmentName;

	public Integer getServiceId() {
		return serviceId;
	}
	public void setServiceId(Integer serviceId) {
		this.serviceId = serviceId;
	}
	public String getServiceName() {
		return serviceName;
	}
	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}
	public String getFormId() {
		return formId;
	}
	public void setFormId(String formId) {
		this.formId = formId;
	}
	public String getTaskId() {
		return taskId;
	}
	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public Integer getBaseServiceId() {
        return baseServiceId;
    }

    public void setBaseServiceId(Integer baseServiceId) {
        this.baseServiceId = baseServiceId;
    }

    @Override
    public String toString() {
        return "Services{"
                .concat("serviceId=").concat(String.valueOf(serviceId))
                .concat(", formId='").concat(formId).concat("'")
                .concat(", taskId='").concat(taskId).concat("'")
                .concat(", taskType='").concat(taskType).concat("'");
    }

    public String getCurrentProcessId() {
        return currentProcessId;
    }

    public void setCurrentProcessId(String currentProcessId) {
        this.currentProcessId = currentProcessId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }
}
