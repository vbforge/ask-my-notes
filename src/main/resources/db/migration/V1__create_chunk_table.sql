CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE chunk (
                       id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       source_file TEXT        NOT NULL,              -- path relative to the notes folder
                       heading     TEXT        NOT NULL DEFAULT '',   -- heading path, e.g. 'Streams > Collectors'
                       chunk_index INTEGER     NOT NULL,              -- order of the chunk within its file
                       content     TEXT        NOT NULL,
                       embedding   vector(768) NOT NULL,              -- nomic-embed-text produces 768 dimensions
                       created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                       CONSTRAINT uq_chunk_file_index UNIQUE (source_file, chunk_index)
);

CREATE INDEX idx_chunk_source_file ON chunk (source_file);