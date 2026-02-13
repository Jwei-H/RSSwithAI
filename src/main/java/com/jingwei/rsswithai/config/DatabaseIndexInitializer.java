package com.jingwei.rsswithai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseIndexInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseIndexInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseIndexInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        logger.info("Starting database index initialization...");

        try {
            // 1. pg_trgm extension
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");

            // 2. GIN indexes for fuzzy search (Idempotent)
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_title_trgm_gin ON articles USING GIN (title gin_trgm_ops)");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_author_trgm_gin ON articles USING GIN (author gin_trgm_ops)");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_source_name_trgm_gin ON articles USING GIN (source_name gin_trgm_ops)");

            // 3. Covered index for list query (Idempotent)
            jdbcTemplate.execute("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_article_source_pubdate_covered " +
                    "ON articles (source_id, pub_date) " +
                    "INCLUDE (source_name, title, word_count, cover_image)");

            // 4. pgvector extension
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");

            // 5. HNSW index for vector search (Idempotent)
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_article_extra_vector_hnsw ON article_extra " +
                    "USING hnsw (vector vector_cosine_ops) " +
                    "WITH (m = 16, ef_construction = 64)");

            logger.info("Database index initialization completed successfully.");
        } catch (Exception e) {
            logger.error("Failed to initialize database indexes: {}", e.getMessage());
            // We usually don't want to crash the app if an index creation fails (e.g.
            // already exists but different definition)
        }
    }
}