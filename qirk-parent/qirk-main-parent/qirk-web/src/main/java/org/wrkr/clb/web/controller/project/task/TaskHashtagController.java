package org.wrkr.clb.web.controller.project.task;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.wrkr.clb.services.dto.project.task.TaskHashtagDTO;
import org.wrkr.clb.services.project.task.TaskHashtagService;
import org.wrkr.clb.services.util.exception.BadRequestException;
import org.wrkr.clb.web.controller.BaseExceptionHandlerController;
import org.wrkr.clb.web.json.JsonContainer;

@RestController
@RequestMapping(path = "task-hashtag", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
public class TaskHashtagController extends BaseExceptionHandlerController {

    @Autowired
    private TaskHashtagService hashtagService;

    @GetMapping(value = "search")
    public JsonContainer<TaskHashtagDTO, Void> list(HttpSession session,
            @RequestParam(name = "prefix", required = false, defaultValue = "") String prefix,
            @RequestParam(name = "project_id", required = false) Long projectId,
            @RequestParam(name = "project_ui_id", required = false) String projectUiId)
            throws BadRequestException {
        long startTime = System.currentTimeMillis();
        List<TaskHashtagDTO> hashtagDTOlist = new ArrayList<TaskHashtagDTO>();
        if (projectId != null) {
            hashtagDTOlist = hashtagService.searchByProjectId(getSessionUser(session), prefix, projectId);
        } else if (projectUiId != null) {
            hashtagDTOlist = hashtagService.searchByProjectUiId(getSessionUser(session), prefix, projectUiId.strip());
        } else {
            throw new BadRequestException("Neither parameter 'project_id' nor parameter 'project_ui_id' is present.");
        }
        logProcessingTimeFromStartTime(startTime, "search", prefix, projectId, projectUiId);
        return new JsonContainer<TaskHashtagDTO, Void>(hashtagDTOlist);
    }
}
