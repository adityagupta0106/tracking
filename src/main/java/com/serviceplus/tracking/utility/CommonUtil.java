package com.serviceplus.tracking.utility;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.serviceplus.tracking.dto.UserSessionDTO;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;

public class CommonUtil {
	
	public static UserSessionDTO getUserSessionDetails(HttpServletRequest request) {
		String user = request.getHeader("USER-DETAILS");
		if(isEmpty(user))
			return null;
			
		return (UserSessionDTO) stringToEntity(user,UserSessionDTO.class);
	}
	
	public static Object stringToEntity(String data,Class<?> classs) {
		return new Gson().fromJson(data, classs);
	}


	public static boolean isEmpty(String value) {
		return value == null || value.trim().isEmpty();
	}
	
	public static String buildUserToken(Integer serviceId, String taskId, Integer locationId) {
		//logic?
	    return serviceId + "~" + taskId + "~" + locationId;
	}

    public static String entityToString(Object data) {
        return new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").create().toJson(data);
    }
	
}
