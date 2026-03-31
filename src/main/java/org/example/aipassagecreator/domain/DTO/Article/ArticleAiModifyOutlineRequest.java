package org.example.aipassagecreator.domain.DTO.Article;

import lombok.Data;

import java.io.Serializable;

@Data
public class ArticleAiModifyOutlineRequest implements Serializable {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 用户的修改建议
     */
    private String modifySuggestion;

    private static final long serialVersionUID = 1L;
}