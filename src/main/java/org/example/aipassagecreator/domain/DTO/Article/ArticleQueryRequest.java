package org.example.aipassagecreator.domain.DTO.Article;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.example.aipassagecreator.common.PageRequest;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class ArticleQueryRequest extends PageRequest implements Serializable {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 状态
     */
    private String status;

    private static final long serialVersionUID = 1L;
}