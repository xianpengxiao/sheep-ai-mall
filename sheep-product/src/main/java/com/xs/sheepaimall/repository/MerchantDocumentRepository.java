package com.xs.sheepaimall.repository;

import com.xs.sheepaimall.entity.MerchantDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MerchantDocumentRepository extends ElasticsearchRepository<MerchantDocument, Long> {
}
