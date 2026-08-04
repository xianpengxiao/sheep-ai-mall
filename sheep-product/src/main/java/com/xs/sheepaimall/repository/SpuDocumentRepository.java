package com.xs.sheepaimall.repository;

import com.xs.sheepaimall.entity.SpuDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpuDocumentRepository extends ElasticsearchRepository<SpuDocument, Long> {
}
