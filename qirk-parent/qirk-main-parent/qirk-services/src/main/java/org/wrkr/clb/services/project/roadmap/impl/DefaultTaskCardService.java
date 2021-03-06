package org.wrkr.clb.services.project.roadmap.impl;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.wrkr.clb.common.util.datetime.DateTimeUtils;
import org.wrkr.clb.model.project.roadmap.TaskCard;
import org.wrkr.clb.model.user.User;
import org.wrkr.clb.repo.project.JDBCProjectRepo;
import org.wrkr.clb.repo.project.roadmap.RoadRepo;
import org.wrkr.clb.repo.project.roadmap.TaskCardRepo;
import org.wrkr.clb.repo.project.task.TaskRepo;
import org.wrkr.clb.services.dto.VersionedEntityDTO;
import org.wrkr.clb.services.dto.project.MoveToRoadDTO;
import org.wrkr.clb.services.dto.project.roadmap.TaskCardDTO;
import org.wrkr.clb.services.dto.project.roadmap.TaskCardReadDTO;
import org.wrkr.clb.services.impl.VersionedEntityService;
import org.wrkr.clb.services.project.roadmap.TaskCardService;
import org.wrkr.clb.services.security.ProjectSecurityService;
import org.wrkr.clb.services.util.exception.ApplicationException;
import org.wrkr.clb.services.util.exception.BadRequestException;
import org.wrkr.clb.services.util.exception.ConflictException;
import org.wrkr.clb.services.util.exception.NotFoundException;
import org.wrkr.clb.services.util.http.JsonStatusCode;

@Validated
@Service
public class DefaultTaskCardService extends VersionedEntityService implements TaskCardService {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(DefaultTaskCardService.class);

    @Autowired
    private TaskCardRepo cardRepo;

    @Autowired
    private RoadRepo roadRepo;

    @Autowired
    private JDBCProjectRepo projectRepo;

    @Autowired
    private TaskRepo taskRepo;

    @Autowired
    private ProjectSecurityService securityService;

    private TaskCard.Status getStatus(String nameCode) throws ApplicationException {
        try {
            return TaskCard.Status.valueOf(nameCode);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(JsonStatusCode.CONSTRAINT_VIOLATION, "Invalid input.", e);
        }
    }

    @Override
    @Transactional(value = "jpaTransactionManager", rollbackFor = Throwable.class)
    public TaskCardReadDTO create(User currentUser, TaskCardDTO cardDTO) throws ApplicationException {
        // security
        securityService.authzCanModifyTaskCardsByRoadId(currentUser, cardDTO.roadId);
        // security

        TaskCard.Status status = getStatus(cardDTO.statusNameCode);

        Long projectId = projectRepo.getProjectIdByRoadId(cardDTO.roadId);
        if (projectId == null) {
            throw new NotFoundException("Project");
        }

        Long nextId = cardRepo.getFirstIdByRoadId(cardDTO.roadId);

        TaskCard card = new TaskCard();
        card.setProjectId(projectId);
        card.setRoadId(cardDTO.roadId);
        card.setName(cardDTO.name);
        card.setStatus(status);
        card.setActive(true);

        card.setCreatedAt(DateTimeUtils.now());
        card = cardRepo.save(card);
        if (nextId != null) {
            cardRepo.updatePreviousId(card.getId(), nextId);
        }

        return TaskCardReadDTO.fromEntity(card);
    }

    @Override
    @Transactional(value = "jpaTransactionManager", rollbackFor = Throwable.class)
    public TaskCardReadDTO update(User currentUser, TaskCardDTO cardDTO) throws ApplicationException {
        // security
        securityService.authzCanModifyTaskCard(currentUser, cardDTO.id);
        // security

        TaskCard.Status status = getStatus(cardDTO.statusNameCode);

        TaskCard card = cardRepo.getActiveById(cardDTO.id);
        if (card == null) {
            throw new NotFoundException("Task card");
        }
        card = checkRecordVersion(card, cardDTO.recordVersion);

        if (card.getStatus().equals(TaskCard.Status.STOPPED) && !status.equals(TaskCard.Status.STOPPED)) {
            taskRepo.updateHiddenByCardId(false, card.getId());
        }
        if (!card.getStatus().equals(TaskCard.Status.STOPPED) && status.equals(TaskCard.Status.STOPPED)) {
            taskRepo.updateHiddenByCardId(true, card.getId());
        }

        card.setName(cardDTO.name);
        card.setStatus(status);
        cardRepo.updateRecordVersionAndNameAndStatus(card);
        return TaskCardReadDTO.fromEntity(card);
    }

    @Override
    @Transactional(value = "jpaTransactionManager", rollbackFor = Throwable.class)
    public void move(User currentUser, MoveToRoadDTO moveDTO) throws ApplicationException {
        // security
        securityService.authzCanModifyTaskCard(currentUser, moveDTO.id);
        // security

        TaskCard card = cardRepo.getActiveForMoveById(moveDTO.id);
        if (card == null) {
            throw new NotFoundException("Task card");
        }
        if (!Objects.equals(moveDTO.oldPreviousId, card.getPreviousId())
                || !Objects.equals(moveDTO.oldNextId, card.getNextId())) {
            throw new ConflictException("Concurrent moving occured.");
        }

        Long newRoadId = moveDTO.roadId;
        if (newRoadId == null) { // move inside the same road
            if (moveDTO.id.equals(moveDTO.previousId)) {
                return; // no move needed
            }
            if (Objects.equals(moveDTO.oldPreviousId, moveDTO.previousId)) {
                return; // no move needed
            }

            newRoadId = card.getRoadId();

        } else {
            if (!roadRepo.existsByIdAndProjectId(newRoadId, card.getProjectId())) {
                throw new NotFoundException("Road");
            }
        }

        if (moveDTO.previousId == null) {
            if (card.getNextId() != null) {
                cardRepo.updatePreviousId(card.getPreviousId(), card.getNextId());
            }

            Long newNextId = cardRepo.getFirstIdByRoadId(newRoadId);
            if (!Objects.equals(moveDTO.nextId, newNextId)) {
                throw new ConflictException("Concurrent moving occured.");
            }

            if (newNextId != null) {
                cardRepo.updatePreviousId(card.getId(), newNextId);
            }
            cardRepo.updateRoadIdAndPreviousIdById(newRoadId, null, card.getId());
            return;

        } else {
            TaskCard newPreviousCard = cardRepo.getActiveForMoveByIdAndRoadId(moveDTO.previousId, newRoadId);
            if (newPreviousCard == null) {
                throw new NotFoundException("Task card");
            }
            if (!Objects.equals(moveDTO.nextId, newPreviousCard.getNextId())) {
                throw new ConflictException("Concurrent moving occured.");
            }

            if (card.getNextId() != null) {
                cardRepo.updatePreviousId(card.getPreviousId(), card.getNextId());
            }
            if (newPreviousCard.getNextId() != null) {
                cardRepo.updatePreviousId(card.getId(), newPreviousCard.getNextId());
            }
            cardRepo.updateRoadIdAndPreviousIdById(newRoadId, newPreviousCard.getId(), card.getId());
        }
    }

    @Override
    public List<TaskCardReadDTO> list(User currentUser, Long projectId) {
        // security
        securityService.authzCanModifyTaskCards(currentUser, projectId);
        // security

        List<TaskCard> cardList = cardRepo.listActiveByProjectIdAndFetchRoad(projectId);
        return TaskCardReadDTO.fromEntities(cardList);
    }

    @Override
    public List<TaskCardReadDTO> list(User currentUser, String projectUiId) {
        // security
        securityService.authzCanModifyTaskCards(currentUser, projectUiId);
        // security

        List<TaskCard> cardList = cardRepo.listActiveByProjectUiIdAndFetchRoad(projectUiId);
        return TaskCardReadDTO.fromEntities(cardList);
    }

    @Override
    @Transactional(value = "jpaTransactionManager", rollbackFor = Throwable.class)
    public void archive(User currentUser, VersionedEntityDTO dto) throws ApplicationException {
        // security
        securityService.authzCanModifyTaskCard(currentUser, dto.id);
        // security

        TaskCard card = cardRepo.getActiveForMoveById(dto.id);
        if (card == null) {
            throw new NotFoundException("Task card");
        }
        card = checkRecordVersion(card, dto.recordVersion);

        card.setActive(false);
        card.setArchievedAt(DateTimeUtils.now());
        cardRepo.updateRecordVersionAndActiveAndArchievedAt(card);

        if (card.getNextId() != null) {
            cardRepo.updatePreviousId(card.getPreviousId(), card.getNextId());
        }
    }

    @Override
    @Transactional(value = "jpaTransactionManager", rollbackFor = Throwable.class, propagation = Propagation.MANDATORY)
    public void archiveBatchByRoadId(Long roadId) {
        cardRepo.setPrevIdToNullActiveToFalseAndArchievedAtToNowAndIncVersionForActiveByRoadId(roadId);
    }

    @Override
    @Transactional(value = "jpaTransactionManager", rollbackFor = Throwable.class, readOnly = true)
    public List<TaskCardReadDTO> listArchive(User currentUser, Long projectId) {
        // security
        securityService.authzCanModifyTaskCards(currentUser, projectId);
        // security

        List<TaskCard> cardList = cardRepo.listNotActiveByProjectIdAndFetchRoadAndOrderByArchievedAtDesc(projectId);
        return TaskCardReadDTO.fromEntities(cardList);
    }

    @Override
    @Transactional(value = "jpaTransactionManager", rollbackFor = Throwable.class, readOnly = true)
    public List<TaskCardReadDTO> listArchive(User currentUser, String projectUiId) {
        // security
        securityService.authzCanModifyTaskCards(currentUser, projectUiId);
        // security

        List<TaskCard> cardList = cardRepo.listNotActiveByProjectUiIdAndFetchRoadAndOrderByArchievedAtDesc(projectUiId);
        return TaskCardReadDTO.fromEntities(cardList);
    }

    @Override
    @Transactional(value = "jpaTransactionManager", rollbackFor = Throwable.class)
    public void delete(User currentUser, Long id) throws ApplicationException {
        // security
        securityService.authzCanModifyTaskCard(currentUser, id);
        // security

        TaskCard card = cardRepo.getForMoveById(id);
        if (card == null) {
            throw new NotFoundException("Task card");
        }

        if (card.getNextId() != null) {
            cardRepo.updatePreviousId(card.getPreviousId(), card.getNextId());
        }

        taskRepo.setCardIdToNullAndHiddenToFalseByCardId(id);
        cardRepo.delete(id);
    }
}
