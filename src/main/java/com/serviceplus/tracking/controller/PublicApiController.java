package com.serviceplus.tracking.controller;

import com.serviceplus.tracking.dto.ApplicationTrackingResponse;
import com.serviceplus.tracking.dto.PublicApplicationTrackingRequest;
import com.serviceplus.tracking.service.ApplicationTrackingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/o")
public class PublicApiController {

    private final ApplicationTrackingService applicationTrackingService;

    @Autowired
    public PublicApiController(ApplicationTrackingService applicationTrackingService) {
        this.applicationTrackingService = applicationTrackingService;
    }

    @RequestMapping(value = "/appl/track/public" , method = RequestMethod.POST )
    public ApplicationTrackingResponse trackApplication(
            @RequestBody PublicApplicationTrackingRequest requestDTO, HttpServletRequest request) {

        return applicationTrackingService.trackPublicApplication(requestDTO,request);
    }

}
