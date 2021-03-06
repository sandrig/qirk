package org.wrkr.clb.services.project.imprt.jira.impl;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.wrkr.clb.common.util.datetime.DateTimeUtils;
import org.wrkr.clb.common.util.strings.MarkdownUtils;
import org.wrkr.clb.common.util.strings.jira.JiraFormatMarkdownUtils;
import org.wrkr.clb.model.project.Project;
import org.wrkr.clb.model.project.ProjectMember;
import org.wrkr.clb.model.project.imprt.jira.ImportedJiraProject;
import org.wrkr.clb.model.project.task.Task;
import org.wrkr.clb.model.user.User;
import org.wrkr.clb.repo.project.JDBCProjectRepo;
import org.wrkr.clb.repo.project.imprt.jira.ImportedJiraProjectRepo;
import org.wrkr.clb.repo.project.task.ProjectTaskNumberSequenceRepo;
import org.wrkr.clb.repo.project.task.TaskRepo;
import org.wrkr.clb.services.api.elasticsearch.ElasticsearchTaskService;
import org.wrkr.clb.services.api.yandexcloud.YandexCloudApiService;
import org.wrkr.clb.services.dto.project.ProjectDTO;
import org.wrkr.clb.services.dto.project.ProjectMemberDTO;
import org.wrkr.clb.services.dto.project.imprt.ImportStatusDTO;
import org.wrkr.clb.services.dto.project.imprt.jira.JiraProjectImportDTO;
import org.wrkr.clb.services.file.YandexCloudFileService;
import org.wrkr.clb.services.project.ProjectMemberService;
import org.wrkr.clb.services.project.ProjectService;
import org.wrkr.clb.services.project.imprt.jira.ImportedJiraProjectService;
import org.wrkr.clb.services.project.imprt.jira.ImportedJiraTaskRetryWrapperService;
import org.wrkr.clb.services.project.imprt.jira.ImportedJiraTaskService;
import org.wrkr.clb.services.project.task.attachment.AttachmentService;
import org.wrkr.clb.services.util.dom.DOMUtils;
import org.wrkr.clb.services.util.exception.ApplicationException;
import org.wrkr.clb.services.util.exception.BadRequestException;

import com.amazonaws.services.s3.model.AmazonS3Exception;

@Service
public class DefaultImportedJiraProjectService implements ImportedJiraProjectService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultImportedJiraProjectService.class);

    private static class JsonStatusCode extends org.wrkr.clb.services.util.http.JsonStatusCode {
        // 400
        public static final String ATTACHMENT_FILE_NOT_FOUND = "ATTACHMENT_FILE_NOT_FOUND";
    }

    @Autowired
    private ImportedJiraProjectRepo importedProjectRepo;

    @Autowired
    private JDBCProjectRepo projectRepo;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectTaskNumberSequenceRepo sequenceRepo;

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private ImportedJiraTaskService importedTaskService;

    @Autowired
    private ImportedJiraTaskRetryWrapperService importedTaskRWService;

    @Autowired
    private TaskRepo taskRepo;

    @Autowired
    private YandexCloudApiService yandexCloudService;

    @Autowired
    private YandexCloudFileService fileService;

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private ElasticsearchTaskService elasticsearchTaskService;

    @Override
    @Transactional(value = "jpaTransactionManager", rollbackFor = Throwable.class, readOnly = true)
    public Map<Long, List<ImportedJiraProject>> mapTimestampToImportedProject() {
        return importedProjectRepo.mapTimestampToImportedProjects();
    }

    private OffsetDateTime getOffsetDateTime(Element element, String attribute) {
        try {
            return DateTimeUtils.parseToOffsetDateTimeAtUTC(
                    DateTimeUtils.JIRA_DATETIME_FORMATTER, element.getAttribute(attribute));
        } catch (Exception e) {
            LOG.error("Exception during parsing datetime", e);
            return DateTimeUtils.now();
        }
    }

    private String generateAttachmentPath(String uploadFolderPath, char delimeter,
            String jiraProjectKey, String jiraAuthorUsername, String taskNumber, String jiraAttachmentId) {
        return String.join(String.valueOf(delimeter),
                uploadFolderPath + "data",
                "attachments",
                jiraProjectKey,
                jiraAuthorUsername,
                jiraProjectKey + "-" + taskNumber,
                jiraAttachmentId);
    }

    private String moveAttachmentFile(String uploadFolderPath, char uploadFolderDelimeter,
            String jiraProjectKey, Map<String, String> jiraUserNameToJiraUserId,
            Task task, String jiraTaskNumber, Element attachmentElement)
            throws Exception {
        String oldAttachmentPath = generateAttachmentPath(uploadFolderPath, uploadFolderDelimeter,
                jiraProjectKey,
                jiraUserNameToJiraUserId.get(attachmentElement.getAttribute("author").toLowerCase()),
                jiraTaskNumber,
                attachmentElement.getAttribute("id"));
        String newAttachmentPath = fileService.generatePathPrefix(task) + attachmentElement.getAttribute("filename");
        try {
            yandexCloudService.copy(oldAttachmentPath, newAttachmentPath);
        } catch (AmazonS3Exception e) {
            if (YandexCloudApiService.ErrorCode.NO_SUCH_KEY.equals(e.getErrorCode())) {
                throw new BadRequestException(JsonStatusCode.ATTACHMENT_FILE_NOT_FOUND,
                        "Could not find attachment file: the archive structure is incorrect.", e);
            }
            throw new ApplicationException("A server error occurred during getting file from to yandex cloud.", e);
        }
        return newAttachmentPath;
    }

    private String getTaskDescriptionMd(Element taskElement) {
        String descriptionJira = taskElement.getAttribute("description");
        if (descriptionJira == null || descriptionJira.isEmpty()) {
            Node descriptionNode = taskElement.getElementsByTagName("description").item(0);
            descriptionJira = (descriptionNode == null ? "" : descriptionNode.getTextContent());
        }
        try {
            descriptionJira = JiraFormatMarkdownUtils.jiraFormatToMarkdown(descriptionJira);
        } catch (Exception e) {
            LOG.error("Could not convert from Jira formatting to HTML", e);
            descriptionJira = MarkdownUtils.escapeMarkdownSpecialSymbols(descriptionJira);
        }
        return MarkdownUtils.escapeMarkdownSpecialSymbols(taskElement.getAttribute("summary")) + "\n\n" +
                descriptionJira;
    }

    private Task importTask(ProjectMember importingMember, Project project, String jiraProjectKey,
            Element taskElement, Long number, Document entitiesDoc, JiraProjectImportDTO importDTO,
            String uploadFolderPath, char uploadFolderDelimeter) throws Exception {
        Task task = new Task();

        task.setProject(project);
        task.setNumber(number);

        task.setDescriptionMd(getTaskDescriptionMd(taskElement));
        task.setDescriptionHtml(MarkdownUtils.markdownToHtml(task.getDescriptionMd(), false));
        task.setSummary(
                MarkdownUtils.descriptionToSummary(task.getDescriptionMd(), task.getDescriptionHtml(), Task.SUMMARY_LENGTH));

        task.setReporter(importDTO.jiraUserNameToQirkProjectMember.getOrDefault(
                taskElement.getAttribute("reporter").toLowerCase(), importingMember));
        task.setAssignee(importDTO.jiraUserNameToQirkProjectMember.get(taskElement.getAttribute("assignee").toLowerCase()));

        task.setCreatedAt(getOffsetDateTime(taskElement, "created"));
        task.setUpdatedAt(getOffsetDateTime(taskElement, "updated"));

        task.setType(importDTO.jiraTypeIdToQirkType.getOrDefault(taskElement.getAttribute("type"), importDTO.defaultType));
        task.setPriority(importDTO.jiraPriorityIdToQirkPriority.getOrDefault(
                taskElement.getAttribute("priority"), importDTO.defaultPriority));
        task.setStatus(
                importDTO.jiraStatusIdToQirkStatus.getOrDefault(taskElement.getAttribute("status"), importDTO.defaultStatus));

        long jiraTaskId = Long.valueOf(taskElement.getAttribute("id"));
        if (task.getNumber() == null) {
            importedTaskRWService.saveAndUpdateTaskNumberSequence(task, jiraTaskId);
        } else {
            importedTaskService.save(task, jiraTaskId);
        }

        NodeList attachmentNodes = DOMUtils.getNodes(
                entitiesDoc, "/entity-engine-xml/FileAttachment[@issue=" + taskElement.getAttribute("id") + "]");
        for (int attachmentIdx = 0; attachmentIdx < attachmentNodes.getLength(); attachmentIdx++) {
            Element attachmentElement = (Element) attachmentNodes.item(attachmentIdx);

            String attachmentPath = moveAttachmentFile(uploadFolderPath, uploadFolderDelimeter,
                    jiraProjectKey, importDTO.jiraUserNameToJiraUserId,
                    task, taskElement.getAttribute("number"), attachmentElement);
            attachmentService.create(attachmentPath, task);
        }

        return task;
    }

    public ProjectMember createImportingProjectMember(Project project, User importingUser) {
        return projectMemberService.create(importingUser, project, new ProjectMemberDTO(true, true));
    }

    public Map<String, ProjectMember> createProjectMembers(Project project, Map<String, User> jiraUserNameToQirkUser) {
        ProjectMemberDTO defaultProjectPermissions = new ProjectMemberDTO(true, false);

        Map<String, ProjectMember> jiraUserNameToQirkProjectMember = new HashMap<String, ProjectMember>();
        for (Entry<String, User> jiraUserNameAndQirkUser : jiraUserNameToQirkUser.entrySet()) {
            ProjectMember projectMember = projectMemberService.create(
                    jiraUserNameAndQirkUser.getValue(), project, defaultProjectPermissions);
            jiraUserNameToQirkProjectMember.put(jiraUserNameAndQirkUser.getKey(), projectMember);
        }
        return jiraUserNameToQirkProjectMember;
    }

    @Override
    @Transactional(value = "jpaTransactionManager", rollbackFor = Throwable.class)
    public void importNewProject(User importingUser,
            Document entitiesDoc, ImportedJiraProject importedProject, JiraProjectImportDTO importDTO,
            String uploadFolderPath, char uploadFolderDelimeter) throws Exception {
        long jiraProjectId = importedProject.getJiraProjectId();
        String jiraProjectKey = importedProject.getJiraProjectKey();

        ProjectDTO projectDTO = new ProjectDTO();

        projectDTO.name = importedProject.getJiraProjectName();
        projectDTO.uiId = "";
        projectDTO.key = jiraProjectKey;
        projectDTO.isPrivate = importDTO.isPrivate;
        projectDTO.description = "";

        Project project = projectService.create(importingUser, projectDTO, new ArrayList<User>());

        importedProject.setProjectId(project.getId());
        importedProject.setUpdatedAt(DateTimeUtils.now());
        importedProjectRepo.save(importedProject);

        ProjectMember importingMember = createImportingProjectMember(project, importingUser);
        importDTO.jiraUserNameToQirkProjectMember = createProjectMembers(project, importDTO.jiraUserNameToQirkUser);

        NodeList taskNodes = DOMUtils.getNodes(entitiesDoc, "/entity-engine-xml/Issue[@project=" + jiraProjectId + "]");
        List<Task> tasks = new ArrayList<Task>(taskNodes.getLength());
        long maxTaskNumber = 0L;
        for (int taskIdx = 0; taskIdx < taskNodes.getLength(); taskIdx++) {
            Element taskElement = (Element) taskNodes.item(taskIdx);

            Task task = importTask(
                    importingMember, project, jiraProjectKey,
                    taskElement, Long.valueOf(taskElement.getAttribute("number")), entitiesDoc, importDTO,
                    uploadFolderPath, uploadFolderDelimeter);

            maxTaskNumber = Long.max(maxTaskNumber, task.getNumber());
            tasks.add(task);
        }

        project.getTaskNumberSequence().setNextTaskNumber(maxTaskNumber + 1);
        sequenceRepo.update(project.getTaskNumberSequence());

        for (Task task : tasks) {
            elasticsearchTaskService.index(task);
        }
    }

    private Task updateTask(Task task, Element taskElement, OffsetDateTime updatedAt, JiraProjectImportDTO importDTO)
            throws IOException {
        task.setDescriptionMd(getTaskDescriptionMd(taskElement));
        task.setDescriptionHtml(MarkdownUtils.markdownToHtml(task.getDescriptionMd(), false));
        task.setSummary(
                MarkdownUtils.descriptionToSummary(task.getDescriptionMd(), task.getDescriptionHtml(), Task.SUMMARY_LENGTH));

        task.setAssignee(importDTO.jiraUserNameToQirkProjectMember.get(taskElement.getAttribute("assignee").toLowerCase()));

        task.setType(importDTO.jiraTypeIdToQirkType.getOrDefault(taskElement.getAttribute("type"), importDTO.defaultType));
        task.setPriority(importDTO.jiraPriorityIdToQirkPriority.getOrDefault(
                taskElement.getAttribute("priority"), importDTO.defaultPriority));
        task.setStatus(importDTO.jiraStatusIdToQirkStatus.getOrDefault(
                taskElement.getAttribute("status"), importDTO.defaultStatus));

        task.setUpdatedAt(updatedAt);
        importedTaskService.update(task);
        return task;
    }

    @Override // no global transaction
    public ImportStatusDTO importProjectUpdate(User importingUser,
            Document entitiesDoc, ImportedJiraProject importedProject, JiraProjectImportDTO importDTO,
            String uploadFolderPath, char uploadFolderDelimeter) throws Exception {
        long jiraProjectId = importedProject.getJiraProjectId();
        String jiraProjectKey = importedProject.getJiraProjectKey();

        OffsetDateTime lastImportAt = importedProjectRepo.getLastUpdatedAtByProjectId(importedProject.getProjectId());
        if (lastImportAt == null) {
            return new ImportStatusDTO(String.valueOf(jiraProjectId), ImportStatusDTO.Status.NOT_FOUND, JsonStatusCode.NOT_FOUND);
        }

        Project project = projectRepo.getByIdWithEverythingForRead(importedProject.getProjectId());

        importedProject.setUpdatedAt(DateTimeUtils.now());
        importedProjectRepo.save(importedProject);

        ProjectMember importingMember = createImportingProjectMember(project, importingUser);
        importDTO.jiraUserNameToQirkProjectMember = createProjectMembers(project, importDTO.jiraUserNameToQirkUser);

        Map<Long, Task> jiraTaskIdToTask = taskRepo.mapJiraTaskIdToTaskByprojectId(project.getId());

        NodeList taskNodes = DOMUtils.getNodes(entitiesDoc, "/entity-engine-xml/Issue[@project=" + jiraProjectId + "]");
        List<Task> updatedTasks = new ArrayList<Task>(taskNodes.getLength());
        List<Long> failedTaskNumbers = new ArrayList<Long>();
        for (int i = 0; i < taskNodes.getLength(); i++) {
            Element taskElement = (Element) taskNodes.item(i);
            Task task = jiraTaskIdToTask.get(Long.valueOf(taskElement.getAttribute("id")));

            if (task == null) {
                task = importTask(
                        importingMember, project, jiraProjectKey,
                        taskElement, null, entitiesDoc, importDTO,
                        uploadFolderPath, uploadFolderDelimeter);
                updatedTasks.add(task);

            } else {
                if (importDTO.overrideTasks.updateTask(task, lastImportAt)) {
                    try {
                        task = updateTask(task, taskElement, importedProject.getUpdatedAt(), importDTO);
                        updatedTasks.add(task);
                    } catch (Exception e) {
                        failedTaskNumbers.add(task.getNumber());
                        LOG.error("Could not update task " + task.getId() + " from jira", e);
                    }
                }
            }
        }

        for (Task task : updatedTasks) {
            try {
                elasticsearchTaskService.updateForJira(task);
            } catch (Exception e) {
                LOG.error("Could not update task " + task.getId() + " to elasticsearch", e);
            }
        }

        if (failedTaskNumbers.isEmpty()) {
            return new ImportStatusDTO(String.valueOf(jiraProjectId), ImportStatusDTO.Status.UPDATED);
        } else {
            return new ImportStatusDTO(
                    String.valueOf(jiraProjectId), ImportStatusDTO.Status.PARTIALLY_UPDATED, failedTaskNumbers);
        }
    }
}
