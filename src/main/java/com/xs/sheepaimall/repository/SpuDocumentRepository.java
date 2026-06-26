package com.xs.sheepaimall.repository;

import com.xs.sheepaimall.entity.SpuDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/** 商品搜索 ES Repository — 仅配置 spring.elasticsearch.uris 后生效 */
@Repository
@ConditionalOnExpression("'${spring.elasticsearch.uris:}' != ''")
public interface SpuDocumentRepository extends ElasticsearchRepository<SpuDocument, Long> {
}
