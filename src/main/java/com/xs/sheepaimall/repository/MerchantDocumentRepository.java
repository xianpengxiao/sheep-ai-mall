package com.xs.sheepaimall.repository;

import com.xs.sheepaimall.entity.MerchantDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "spring.elasticsearch.uris")
public interface MerchantDocumentRepository extends ElasticsearchRepository<MerchantDocument, Long> {
}
