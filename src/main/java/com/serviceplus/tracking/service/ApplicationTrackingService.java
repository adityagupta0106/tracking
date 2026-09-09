package com.serviceplus.tracking.service;

import com.serviceplus.tracking.dto.*;
import com.serviceplus.tracking.entity.ApplicationTracking;
import com.serviceplus.tracking.entity.ApplicationTrackingHolder;
import com.serviceplus.tracking.feignClient.InstanceConfigRegistryFeignClient;
import com.serviceplus.tracking.repository.ApplicationTrackingDocumentRepository;
import com.serviceplus.tracking.repository.ApplicationTrackingHolderRepository;
import com.serviceplus.tracking.repository.ApplicationTrackingRepository;
import com.serviceplus.tracking.repository.ApplicationTrackingRepositoryCustomImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.serviceplus.tracking.utility.ApplicationConstants.APPLICATION_SUBMISSION_TASK_NAME;
import static com.serviceplus.tracking.utility.ApplicationConstants.CLIENT_DOMAIN_HEADER;
import static com.serviceplus.tracking.utility.CommonUtil.getUserSessionDetails;
import static com.serviceplus.tracking.utility.CommonUtil.isEmpty;

@Service
public class ApplicationTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationTrackingService.class);

    private final ApplicationTrackingRepository trackingRepository;

    private final WorkflowInboxRedisService workflowInboxRedisService;

    private final ApplicationTrackingRepositoryCustomImpl applicationTrackingRepositoryCustom;

    private final InstanceConfigRegistryFeignClient instanceConfigRegistryFeignClient;

    private final ApplicationTrackingHolderRepository applicationTrackingHolderRepository;

    private final ApplicationTrackingDocumentRepository applicationTrackingDocumentRepository;

    public ApplicationTrackingService(ApplicationTrackingRepository trackingRepository, WorkflowInboxRedisService workflowInboxRedisService, ApplicationTrackingRepositoryCustomImpl applicationTrackingRepositoryCustom, InstanceConfigRegistryFeignClient instanceConfigRegistryFeignClient, ApplicationTrackingHolderRepository applicationTrackingHolderRepository, ApplicationTrackingDocumentRepository applicationTrackingDocumentRepository) {
        this.trackingRepository = trackingRepository;
        this.workflowInboxRedisService = workflowInboxRedisService;
        this.applicationTrackingRepositoryCustom = applicationTrackingRepositoryCustom;
        this.instanceConfigRegistryFeignClient = instanceConfigRegistryFeignClient;
        this.applicationTrackingHolderRepository = applicationTrackingHolderRepository;
        this.applicationTrackingDocumentRepository = applicationTrackingDocumentRepository;
    }

    public ApplicationTrackingResponse getTracking(ApplicationTrackingRequest requestDTO, HttpServletRequest request) {

        UserSessionDTO session = getUserSessionDetails(request);

        logger.info("Tracking request received. userId={}, applId={}, applRefNo={}", session.getUserID(), requestDTO.getApplId(), requestDTO.getApplRefNo());

        Set<Object> holderIds = workflowInboxRedisService.getTokensByUserAndLocation(
                                session.getUserID(),
                                String.valueOf(session.getLocationId()));

        boolean officialUser = holderIds != null && !holderIds.isEmpty();

        Set<String> officialProcessIds = Collections.emptySet();

        if (officialUser) {

            logger.info("Official flow detected. holderCount={}", holderIds.size());
            Set<String> holderIdStrings = holderIds.stream().map(String::valueOf).collect(Collectors.toSet());

            List<ApplicationTrackingHolder> holderMappings =applicationTrackingHolderRepository
                                                            .findByHolderIdInAndTenantId(
                                                                    holderIdStrings,
                                                                    session.getTenantId());

            officialProcessIds = holderMappings.stream().map(ApplicationTrackingHolder::getProcessId).collect(Collectors.toSet());

            logger.info("Official has access to {} process ids", officialProcessIds.size());

        } else {

            logger.info("Citizen flow detected. userId={}", session.getUserID());
        }

        List<ApplicationTracking> trackingList;

        if (!isEmpty(requestDTO.getApplId())) {

            trackingList = trackingRepository
                            .findByApplIdAndTenantIdOrderByProcessIdAsc(
                                    requestDTO.getApplId(),
                                    session.getTenantId());

        } else {

            trackingList = trackingRepository
                            .findByApplRefNoAndTenantIdOrderByProcessIdAsc(
                                    requestDTO.getApplRefNo(),
                                    session.getTenantId());
        }

        logger.info("Tracking records fetched count={}", trackingList.size());

        if (trackingList.isEmpty()) {
            return new ApplicationTrackingResponse();
        }

        return convertToDTO(trackingList, officialUser, session.getUserID(), officialProcessIds,holderIds);
    }

    private ApplicationTrackingResponse convertToDTO(List<ApplicationTracking> trackingList, boolean officialUser, Long sessionUserId, Set<String> officialProcessIds, Set<Object> holderIds) {

        ApplicationTracking first = trackingList.getFirst();

        first.setTaskName(APPLICATION_SUBMISSION_TASK_NAME);

        ApplicationTrackingResponse response = new ApplicationTrackingResponse();

        response.setApplId(first.getApplId());
        response.setApplRefNo(first.getApplRefNo());
        response.setServiceName(first.getServiceName());
        response.setBeneficiaryUserName(first.getBeneficiaryUserName());
        response.setApplyDate(first.getApplyDate());

        response.setTrackingDetails(
                buildTrackingDetails(
                        trackingList,
                        officialUser,
                        sessionUserId,
                        officialProcessIds,
                        holderIds));

        return response;
    }

    private List<ApplicationTrackingResponse.TrackingDetailDTO> buildTrackingDetails(List<ApplicationTracking> trackingList, boolean officialUser, Long sessionUserId, Set<String> officialProcessIds, Set<Object> holderIds) {

        List<ApplicationTrackingResponse.TrackingDetailDTO> result = new ArrayList<>();

        boolean applicantAccess = trackingList.stream()
                                              .anyMatch(tracking ->
                                                      tracking.getAppliedUserId() != null && tracking.getAppliedUserId().equals(sessionUserId)
                                              );

        for (ApplicationTracking tracking : trackingList) {

            ApplicationTrackingResponse.TrackingDetailDTO dto = new ApplicationTrackingResponse.TrackingDetailDTO();

            ApplicationTrackingResponse.TrackingPermissions permissions = new ApplicationTrackingResponse.TrackingPermissions();

            dto.setProcessId(tracking.getProcessId());
            dto.setTaskName(tracking.getTaskName());
            dto.setActionTaken(tracking.getActionTaken());
            dto.setActionOn(tracking.getActionOn());
            dto.setActionCode(tracking.getActionCode());
            dto.setActionName(tracking.getActionName());

            boolean officialAccess = false;

            if (officialUser) {

                officialAccess = officialProcessIds.contains(tracking.getProcessId());

                if (!officialAccess) {

                    Set<Object> serviceHolderIds = workflowInboxRedisService.getHolderIdsByService(tracking.getServiceId());

                    if (serviceHolderIds != null && !serviceHolderIds.isEmpty()) {

                        Set<String> serviceHolders = serviceHolderIds.stream().map(String::valueOf).collect(Collectors.toSet());

                        officialAccess = holderIds.stream().map(String::valueOf).anyMatch(serviceHolders::contains);
                    }
                }
            }

            if (applicantAccess || officialAccess) {

                dto.setFormId(tracking.getFormId());
                dto.setDataId(tracking.getDataId());

                final boolean finalOfficialAccess = officialAccess;
                final boolean finalApplicantAccess = applicantAccess;

                List<ApplicationTrackingResponse.TrackingDetailDTO.DocumentDTO> documents =
                        applicationTrackingDocumentRepository
                                .findByTrackingProcessId(tracking.getProcessId())
                                .stream()
                                .filter(entity -> {

                                    List<String> viewPermission = entity.getViewPermission();

                                    if (viewPermission == null || viewPermission.isEmpty()) {
                                        return false;
                                    }

                                    if (finalApplicantAccess && viewPermission.contains("applicant")) {
                                        return true;
                                    }

                                    // Official must have access AND document
                                    // must allow official viewing.
                                    return finalOfficialAccess && viewPermission.contains("official");
                                })
                                .map(entity -> {

                                    ApplicationTrackingResponse.TrackingDetailDTO.DocumentDTO document =
                                            new ApplicationTrackingResponse.TrackingDetailDTO.DocumentDTO();

                                    document.setDocumentId(entity.getId());
                                    document.setDocumentName(entity.getDocumentName());

                                    return document;
                                })
                                .toList();

                dto.setDocuments(documents);
            }

            dto.setPermissions(permissions);
            result.add(dto);
        }

        return result;
    }

    public ApplicationTrackingResponse trackPublicApplication(PublicApplicationTrackingRequest requestDTO, HttpServletRequest request) {

        LocalDateTime applyDate =
                LocalDate.parse(
                                requestDTO.getApplyDate(),
                                DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        .atStartOfDay();

        String tenantId = ""; // request.getHeader("x-tenant-id");

        if (isEmpty(tenantId)) {

            ResponseEntity<?> responseEntity =
                    instanceConfigRegistryFeignClient.fetchTenantId(
                            Map.of(
                                    CLIENT_DOMAIN_HEADER,
                                    request.getHeader(CLIENT_DOMAIN_HEADER)));

            tenantId = (String) responseEntity.getBody();
        }

        List<ApplicationTracking> trackingList =trackingRepository
                                                .findByApplRefNoAndApplyDateAndTenantIdOrderByProcessIdAsc(
                                                        requestDTO.getApplRefNo(),
                                                        applyDate,
                                                        tenantId);

        if (trackingList.isEmpty()) {

            logger.info("No tracking records found for applRefNo={} applyDate={}", requestDTO.getApplRefNo(), requestDTO.getApplyDate());
            return new ApplicationTrackingResponse();
        }

        logger.info("Public tracking fetched successfully. applRefNo={}, recordCount={}", requestDTO.getApplRefNo(), trackingList.size());

        return convertToDTO(
                trackingList,
                false,
                null,
                Collections.emptySet(), new HashSet<>());
    }

    public ServerSidePaginationRecord<Applications> trackList(ApplicationSearchRequest requestDTO, HttpServletRequest request) {
        return applicationTrackingRepositoryCustom.search(requestDTO, Objects.requireNonNull(getUserSessionDetails(request)));
    }

    public DocumentDownloadResponse getDocument(Long documentId, UserSessionDTO user) {
        return applicationTrackingRepositoryCustom.getDocument(documentId, user);
    }
}