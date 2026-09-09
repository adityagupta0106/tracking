package com.serviceplus.tracking.service;

import static com.serviceplus.tracking.utility.CommonUtil.buildUserToken;
import static com.serviceplus.tracking.utility.CommonUtil.isEmpty;
import static java.util.Objects.isNull;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.serviceplus.tracking.dto.*;
import com.serviceplus.tracking.entity.*;
import com.serviceplus.tracking.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.serviceplus.tracking.dto.ApplicationCurrentProcess;
import com.serviceplus.tracking.dto.OfficeDetailsDTO;
import com.serviceplus.tracking.dto.UserInboxDTO;
import com.serviceplus.tracking.dto.UserInboxKafkaDTO;
import com.serviceplus.tracking.entity.ApplicationTracking;
import com.serviceplus.tracking.entity.ApplicationTrackingHolder;
import com.serviceplus.tracking.entity.UserInbox;
import com.serviceplus.tracking.entity.UserSentBox;
import com.serviceplus.tracking.repository.ApplicationTrackingHolderRepository;
import com.serviceplus.tracking.repository.ApplicationTrackingRepository;
import com.serviceplus.tracking.repository.ServiceMasterRepository;
import com.serviceplus.tracking.repository.TaskMasterRepository;
import com.serviceplus.tracking.repository.UserInboxRepository;
import com.serviceplus.tracking.repository.UserSentBoxRepository;

@Service
public class InboxConsumerService {

    private static final Logger log = LoggerFactory.getLogger(InboxConsumerService.class);


    private final UserInboxRepository userInboxRepository;

    private final UserSentBoxRepository userSentBoxRepository;

    private final ApplicationTrackingRepository applicationTrackingRepository;

    private final WorkflowInboxRedisService workflowInboxRedisService;

    private final ApplicationTrackingHolderRepository trackingHolderRepository;

    private final ApplicationTrackingDocumentRepository applicationTrackingDocumentRepository;

    public InboxConsumerService(UserInboxRepository userInboxRepository, UserSentBoxRepository userSentBoxRepository, ApplicationTrackingRepository applicationTrackingRepository, WorkflowInboxRedisService workflowInboxRedisService, ApplicationTrackingHolderRepository trackingHolderRepository, ApplicationTrackingDocumentRepository applicationTrackingDocumentRepository) {
        this.userInboxRepository = userInboxRepository;
        this.userSentBoxRepository = userSentBoxRepository;
        this.applicationTrackingRepository = applicationTrackingRepository;
        this.workflowInboxRedisService = workflowInboxRedisService;
        this.trackingHolderRepository = trackingHolderRepository;
        this.applicationTrackingDocumentRepository = applicationTrackingDocumentRepository;
    }

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Transactional
    public void updateUserInboxFromKafka(String key, String message, String topic) {

        try {
            log.info("Received inbox Kafka message. Topic: {}, Key: {}", topic, key);
            System.out.println(message);

            UserInboxKafkaDTO kafkaDTO = objectMapper.readValue(message, UserInboxKafkaDTO.class);
            processApplicationTracking(kafkaDTO);
            processSentBox(kafkaDTO);
            processInbox(kafkaDTO);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processInbox(UserInboxKafkaDTO kafkaDTO) {

        List<UserInboxDTO> inboxList = mapDTOValues(kafkaDTO);

        for (UserInboxDTO dto : inboxList) {
            persistInboxEntries(dto);
        }

        log.info("Inbox processing completed for applicationRefNo: {}", kafkaDTO.getApplicationRefNo());
    }

    private void processSentBox(UserInboxKafkaDTO kafkaDTO) {

        if (kafkaDTO == null || kafkaDTO.getProcessList() == null) {
            return;
        }

        List<UserSentBox> sentList = new ArrayList<>();

        for (ApplicationCurrentProcess process : kafkaDTO.getProcessList()) {

            if ("N".equals(process.getActionTaken()) || isEmpty(process.getPreviousProcessId()) || process.getGateway()) {
                continue;
            }

            Set<Object> holderIds = workflowInboxRedisService.getTokenByServiceTaskAndUser(
                                            process.getServiceId(),
                                            process.getCurrentTask(),
                                            kafkaDTO.getLoggedInUserId(),
                                            String.valueOf(kafkaDTO.getLoggedInUserLocation())
                                    );

            if (holderIds == null || holderIds.isEmpty()) {
                log.warn("No holder token found for serviceId={}, taskId={}, userId={}, locationId={}", process.getServiceId(), process.getCurrentTask(), kafkaDTO.getLoggedInUserId(), kafkaDTO.getLoggedInUserLocation());
                 continue;
            }

            Set<String> holderIdStrings = holderIds.stream()
                                                .map(String::valueOf)
                                                .map(String::trim)
                                                .collect(Collectors.toSet());

            String processId = process.getProcessId() == null ? null : process.getProcessId().trim();

            String tenantId = process.getTenantId() == null ? null : process.getTenantId().trim();

            List<UserInbox> inboxList =
                                        userInboxRepository
                                                .findByCurrentProcessIdAndUserTokenInAndTenantId(
                                                        processId,
                                                        holderIdStrings,
                                                        tenantId
                                                );

            if (inboxList.isEmpty()) {
                log.warn("No inbox entries found for processId={}, holderIds={}", process.getProcessId(), holderIdStrings);
                continue;
            }

            for (UserInbox inbox : inboxList) {
                sentList.add(getUserSentBox(kafkaDTO, process, inbox));
            }

            userInboxRepository.deleteAll(inboxList);
        }

        if (!sentList.isEmpty()) {

            log.info("Saving {} sent box entries", sentList.size());
            userSentBoxRepository.saveAll(sentList);
        }
    }

    private static UserSentBox getUserSentBox(UserInboxKafkaDTO kafkaDTO, ApplicationCurrentProcess process, UserInbox inbox) {

        UserSentBox sent = new UserSentBox();

        sent.setBaseServiceId(inbox.getBaseServiceId());
        sent.setServiceId(inbox.getServiceId());
        sent.setServiceName(inbox.getServiceName());

        sent.setApplId(inbox.getApplId());
        sent.setApplRefNo(inbox.getApplRefNo());

        sent.setSubmissionDate(kafkaDTO.getApplyDate());
        sent.setProcessId(process.getProcessId());
        sent.setTaskId(inbox.getTaskId());
        sent.setTaskName(inbox.getTaskName());

        sent.setCalledback(Boolean.FALSE);
        sent.setHolderId(inbox.getUserToken());

        sent.setActionCode(process.getActionCode());
        sent.setActionUserId(process.getUserId().intValue());

        sent.setActionOn(process.getActionOn());
        sent.setActionOnDate(process.getActionOn());

        sent.setTenantId(process.getTenantId());

        return sent;
    }

    private List<UserInboxDTO> mapDTOValues(UserInboxKafkaDTO kafkaDTO) {

        List<UserInboxDTO> result = new ArrayList<>();

        if (kafkaDTO == null || kafkaDTO.getProcessList() == null) {
            log.warn("Received null Kafka DTO or process list");
            return result;
        }
        //System.out.println(kafkaDTO.getProcessList().toString());

        log.info("Processing {} current process records", kafkaDTO.getProcessList().size());

        for (ApplicationCurrentProcess process : kafkaDTO.getProcessList()) {

            if (!"N".equals(process.getActionTaken())) {
                log.debug("Skipping processId {} as actionTaken={}", process.getProcessId(), process.getActionTaken());
                //complete closure case
                if(kafkaDTO.isCompleteClosure()) {
                	userInboxRepository.deleteByApplIdAndTenantIdAndBaseServiceId(process.getApplicationId(),process.getTenantId(),process.getBaseServiceId());
                }
                continue;
            }

            UserInboxDTO dto = new UserInboxDTO();

            dto.setApplId(process.getApplicationId());
            dto.setApplRefNo(kafkaDTO.getApplicationRefNo());
            dto.setServiceId(process.getServiceId());
            dto.setServiceName(kafkaDTO.getServiceName());
            dto.setApplRecievedOn(process.getInitiatedOn());
            dto.setLastActionOn(process.getActionOn());
            dto.setTenantId(process.getTenantId());
            dto.setBaseServiceId(process.getBaseServiceId());

            UserInboxDTO.TaskDTO task = new UserInboxDTO.TaskDTO();
            task.setFormId(process.getFormId());
            task.setTaskId(process.getCurrentTask());
            task.setTaskName(process.getCurrentTaskName());
            task.setCurrentProcessId(process.getProcessId());
            task.setIsPriority(process.getIsPriority());

            List<UserInboxDTO.LocationDTO> locations = new ArrayList<>();

            if (kafkaDTO.getOfficeDetails() != null) {

                for (OfficeDetailsDTO office : kafkaDTO.getOfficeDetails()) {

                    if (!process.getCurrentTask().equals(office.getTaskId())) {
                        continue;
                    }

                    if (office.getAllowedOffices() == null) {
                        continue;
                    }

                    for (OfficeDetailsDTO.OfficeUnitData unit : office.getAllowedOffices()) {

                        if (unit.getHolderIds() == null || unit.getHolderIds().isEmpty()) {
                            continue;
                        }

                        for (String holderId : unit.getHolderIds()) {

                            UserInboxDTO.LocationDTO loc = new UserInboxDTO.LocationDTO();

                            loc.setLocationId(unit.getOrgUnitCode());
                            loc.setLocationName(unit.getOrgUnitName());
                            loc.setUserToken(holderId);

                            locations.add(loc);
                        }
                    }
                }
            }

            task.setLocations(locations);
            dto.setTask(task);

            result.add(dto);

            log.debug("Mapped taskId {} with {} location-user mappings", task.getTaskId(), locations.size());
        }

        log.info("Total mapped inbox DTOs: {}", result.size());

        return result;
    }



    private static UserInboxDTO.TaskDTO getTaskDTO(UserInboxKafkaDTO kafkaDTO, ApplicationCurrentProcess process) {
        UserInboxDTO.TaskDTO task = new UserInboxDTO.TaskDTO();

        task.setFormId(process.getFormId());
        task.setTaskId(process.getCurrentTask());
        task.setTaskName(process.getCurrentTaskName());
        task.setCurrentProcessId(process.getProcessId());
        List<UserInboxDTO.LocationDTO> locations = new ArrayList<>();

        for (OfficeDetailsDTO office : kafkaDTO.getOfficeDetails()) {

            if (!process.getCurrentTask().equals(office.getTaskId())) {
                continue;
            }

            if (office.getAllowedOffices() == null) {
                continue;
            }

            for (OfficeDetailsDTO.OfficeUnitData unit : office.getAllowedOffices()) {

                UserInboxDTO.LocationDTO loc = getLocationDTO(process, unit);
                locations.add(loc);
            }
        }

        task.setLocations(locations);
        return task;
    }

    private static UserInboxDTO.LocationDTO getLocationDTO(ApplicationCurrentProcess process, OfficeDetailsDTO.OfficeUnitData unit) {
        UserInboxDTO.LocationDTO loc = new UserInboxDTO.LocationDTO();

        loc.setLocationId(unit.getOrgUnitCode());
        loc.setLocationName(unit.getOrgUnitName());

        loc.setUserToken(buildUserToken(process.getServiceId(), process.getCurrentTask(), unit.getOrgUnitCode()));
        return loc;
    }

    public void persistInboxEntries(UserInboxDTO dto) {

        UserInboxDTO.TaskDTO task = dto.getTask();

        List<UserInbox> inboxList = new ArrayList<>();

        for (UserInboxDTO.LocationDTO loc : task.getLocations()) {

            UserInbox inbox = new UserInbox();

            inbox.setApplId(dto.getApplId());
            inbox.setApplRefNo(dto.getApplRefNo());
            inbox.setServiceId(dto.getServiceId());
            inbox.setBaseServiceId(dto.getBaseServiceId());
            inbox.setServiceName(dto.getServiceName());
            inbox.setFormId(task.getFormId());
            inbox.setTaskId(task.getTaskId());
            inbox.setTaskName(task.getTaskName());
            inbox.setCurrentProcessId(task.getCurrentProcessId());
            inbox.setIsPriority(task.getIsPriority() != null ? task.getIsPriority() : false);
            inbox.setLocationId(loc.getLocationId());
            inbox.setUserToken(loc.getUserToken());
            inbox.setApplRecievedOn(Timestamp.valueOf(dto.getApplRecievedOn()));
            inbox.setTenantId(dto.getTenantId());

            inboxList.add(inbox);
        }

        if (!inboxList.isEmpty()) {
            log.info("Persisting {} inbox entries for applicationId: {}, taskId: {}", inboxList.size(), dto.getApplId(),
                    task.getTaskId());

            inboxList = userInboxRepository.saveAll(inboxList);
        }
        else {
            log.warn("No inbox entries found to persist for applicationId: {}, taskId: {}", dto.getApplId(),
                    task.getTaskId());
        }


}

    private void processApplicationTracking(UserInboxKafkaDTO kafkaDTO) {

        if (isNull(kafkaDTO) || kafkaDTO.getProcessList() == null || kafkaDTO.getProcessList().isEmpty()) {
            return;
        }

        List<ApplicationTracking> trackingList = new ArrayList<>();
        List<ApplicationTrackingDocument> trackingDocumentList = new ArrayList<>();
        List<ApplicationTrackingHolder> holderList = new ArrayList<>();

        for (ApplicationCurrentProcess process : kafkaDTO.getProcessList()) {

            if (process.getGateway()) {
                continue;
            }

            Optional<ApplicationTracking> existingTracking =
                    applicationTrackingRepository.findByProcessIdAndTenantId(
                            process.getProcessId(),
                            process.getTenantId());

            ApplicationTracking tracking =
                    existingTracking.orElse(new ApplicationTracking());

            boolean isNew = existingTracking.isEmpty();

            tracking.setProcessId(process.getProcessId());
            tracking.setApplId(process.getApplicationId());
            tracking.setApplRefNo(kafkaDTO.getApplicationRefNo());

            tracking.setServiceId(process.getServiceId());
            tracking.setBaseServiceId(process.getBaseServiceId());
            tracking.setServiceName(kafkaDTO.getServiceName());

            tracking.setTaskId(process.getCurrentTask());
            tracking.setTaskName(process.getCurrentTaskName());

            tracking.setPreviousTaskId(process.getPreviousTask());
            tracking.setPreviousTaskName(process.getPreviousTaskName());

            tracking.setActionCode(process.getActionCode());
            tracking.setActionTaken(process.getActionTaken());
            tracking.setActionName(process.getActionName());

            tracking.setBeneficiaryUserName(kafkaDTO.getBeneficiaryName());

            tracking.setApplyDate(kafkaDTO.getApplyDate());
            tracking.setActionOn(process.getActionOn());

            tracking.setTenantId(process.getTenantId());

            if (process.getApplicantTask()) {
                tracking.setAppliedUserId(kafkaDTO.getAppliedBy());

            } else {
                tracking.setAppliedUserId(null);
            }
            tracking.setFormId(process.getFormId());
            tracking.setDataId(process.getDataId());

            trackingList.add(tracking);

            /*
             * Save tracking documents.
             */
            if ("Y".equals(process.getActionTaken()) && process.getDocuments() != null && !process.getDocuments().isEmpty()) {

                for (TrackingDocument document : process.getDocuments()) {

                    ApplicationTrackingDocument trackingDocument = new ApplicationTrackingDocument();

                    trackingDocument.setDocumentId(document.getUploadId());
                    trackingDocument.setDocumentName(document.getDocumentName());
                    trackingDocument.setDocumentType(document.getSourceType());
                    trackingDocument.setTenantId(process.getTenantId());
                    trackingDocument.setViewPermission(document.getViewPermission());

                    trackingDocument.setTracking(tracking);

                    trackingDocumentList.add(trackingDocument);
                }
            }

            /*
             * Save holder mapping only for newly created workflow tasks.
             */
            if (isNew && kafkaDTO.getOfficeDetails() != null) {

                for (OfficeDetailsDTO office : kafkaDTO.getOfficeDetails()) {

                    if (!process.getCurrentTask().equals(office.getTaskId())) {
                        continue;
                    }

                    if (office.getAllowedOffices() == null) {
                        continue;
                    }

                    for (OfficeDetailsDTO.OfficeUnitData unit : office.getAllowedOffices()) {

                        if (unit.getHolderIds() == null || unit.getHolderIds().isEmpty()) {
                            continue;
                        }

                        for (String holderId : unit.getHolderIds()) {

                            ApplicationTrackingHolder holder =
                                    new ApplicationTrackingHolder();

                            holder.setProcessId(process.getProcessId());
                            holder.setHolderId(holderId);
                            holder.setLocationId(unit.getOrgUnitCode());
                            holder.setLocationName(unit.getOrgUnitName());
                            holder.setTenantId(process.getTenantId());

                            holderList.add(holder);
                        }
                    }
                }
            }
        }

        if (!trackingList.isEmpty()) {

            log.info("Saving {} application tracking records", trackingList.size());

            applicationTrackingRepository.saveAll(trackingList);
        }

        if (!trackingDocumentList.isEmpty()) {

            log.info("Saving {} application tracking document records", trackingDocumentList.size());

            applicationTrackingDocumentRepository.saveAll(trackingDocumentList);
        }

        if (!holderList.isEmpty()) {

            log.info("Saving {} application tracking holder mappings", holderList.size());

            trackingHolderRepository.saveAll(holderList);
        }
    }

}