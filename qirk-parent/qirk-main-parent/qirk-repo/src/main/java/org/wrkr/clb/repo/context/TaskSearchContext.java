package org.wrkr.clb.repo.context;

import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.wrkr.clb.common.validation.IdAndUiIdObject;
import org.wrkr.clb.model.project.task.TaskPriority;
import org.wrkr.clb.model.project.task.TaskStatus;
import org.wrkr.clb.model.project.task.TaskType;
import org.wrkr.clb.repo.sort.SortingOption;
import org.wrkr.clb.repo.sort.SortingOption.ForTask;
import org.wrkr.clb.repo.sort.SortingOption.Order;

public class TaskSearchContext {

    public static final Long REPORTED_BY_ME_ID = -1L;
    public static final Long ASSIGNED_TO_ME_ID = -1L;
    public static final Long UNASSIGNED_ID = -2L;

    @NotNull(message = "projectDTO in TaskSearchContext must not be null")
    @Valid
    public IdAndUiIdObject projectDTO;

    public Long reporterId;
    public Long assigneeId;

    public List<String> types = new ArrayList<String>();
    public List<String> priorities = new ArrayList<String>();
    public List<String> statuses = new ArrayList<String>();

    public String hashtag;
    public String text;

    public List<Long> excludeCards;

    @NotNull(message = "sortBy in TaskSearchContext must not be null")
    public SortingOption.ForTask sortBy;
    @NotNull(message = "sortOrder in TaskSearchContext must not be null")
    public SortingOption.Order sortOrder;
    public Long searchAfter;

    private <T extends Enum<T>> void setEnumList(Class<T> enumClass, List<String> enumList, List<String> values) {
        for (String element : values) {
            try {
                enumList.add(Enum.valueOf(enumClass, element.strip().toUpperCase()).toString());
            } catch (IllegalArgumentException e) {
            }
        }
    }

    public TaskSearchContext(IdAndUiIdObject projectDTO, Long reporterId, Long assigneeId,
            List<String> types, List<String> priorities, List<String> statuses,
            String text, String hashtag,
            ForTask sortBy, Order sortOrder, Long searchAfter) {
        this.projectDTO = projectDTO;
        this.reporterId = reporterId;
        this.assigneeId = assigneeId;

        setEnumList(TaskType.Type.class, this.types, types);
        setEnumList(TaskPriority.Priority.class, this.priorities, priorities);
        setEnumList(TaskStatus.Status.class, this.statuses, statuses);

        this.hashtag = hashtag.strip();
        this.text = text.strip();

        this.sortBy = sortBy;
        this.sortOrder = sortOrder;
        this.searchAfter = searchAfter;
    }
}
