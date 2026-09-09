package com.serviceplus.tracking.feignClient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "UserManagement",path = "/usermgmt")
public interface UserManagmentFeignClient {

	@GetMapping("/checkUserAssigned")
	public ResponseEntity<?> checkUserAssigned(@RequestParam String userToken ,@RequestParam Long userId);
}
