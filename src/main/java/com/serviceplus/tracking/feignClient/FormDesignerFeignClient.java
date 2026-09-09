package com.serviceplus.tracking.feignClient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.serviceplus.tracking.dto.FormAttributeRequest;

@FeignClient(name = "formDesigner", path = "/formmgmt")
public interface FormDesignerFeignClient {

	@PostMapping("/filter-attributes")
	public ResponseEntity<?> getFilterAttributes(@RequestBody FormAttributeRequest formAttributeRequest);

}
