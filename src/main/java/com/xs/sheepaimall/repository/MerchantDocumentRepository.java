package com.xs.sheepaimall.repository;

import com.xs.sheepaimall.entity.MerchantDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnExpression("'${spring.elasticsearch.uris:}' != ''")
public interface MerchantDocumentRepository extends ElasticsearchRepository<MerchantDocument, Long> {
}
