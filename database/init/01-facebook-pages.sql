CREATE TABLE IF NOT EXISTS facebook_pages (
    id            BIGSERIAL PRIMARY KEY,
    page_id       VARCHAR(64)  NOT NULL UNIQUE,
    page_name     VARCHAR(255) NOT NULL,
    access_token  VARCHAR(512) NOT NULL,
    ad_account_id VARCHAR(64),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP
);

INSERT INTO facebook_pages (
    page_id,
    page_name,
    access_token,
    ad_account_id
)
VALUES (
    '635100196572436',
    'Cambodia Online Shopping',
    'EAAOuobP4aYEBSNBODGyepZAdFEkMrhKwgZAir9XqK3hbmFStvExHZBCq5cqqKZCxgka9vce1rCdj3SZBmoozl2ZAYMXLhhjQGrvxKwsIcWhoPZCvJrw5C1VGCajhDql08GvdthUYW81EF19Oc7wSp1DiIu7jjGcFBO7ZBtg8jlRiIW0a5cZBICx7IOkQZA0xQmbVywZCZCvZCs9wZAZBJg6hxMgQtUKdu8s',
    '1218704455475267'
),
('951226198077877', '3333 បោះដុំ លក់រាយ', 'EAAOuobP4aYEBSHnV9giNumoJ7ZAkJcZA40yDAT87ZCbp18BBZABDgp4S8IDK9gCUQjkM2r98K7eweijgjUcfcArGGtym1HQDZBG16LZBsaA3sIMBqmFPsPrPHgJw46ccYcdWizp6H2FRHVnO4CLQiZCjSGuZBLzLAeZAPciyoClSKUIOxmSIqh63Mo22b5XGFgF9ZCvZAtan0lnHMZBZABkAZBqGg84NON', '635100196572436'),
('989127009187412', '168 1', 'EAAOuobP4aYEBSBnv0BvaHjpTMh0WP4ZAlFcP0B5sTr11hNZBmTjuBavpAVrPSs9VPipZBZCElI2sJShARYLr5QzCQGR87GoeY7KyQQjorrsLB0WExLj0iFLSlrn7bBfaww9rAVbUzZAy1Nb9MsDTP3fbfqC9TxwYKR2FtZCZBNezUALDlVTuV3rn7ktDJufCEpVscoQrkN1IAsqXwwAbluwyaIZD', '107021082423530')
ON CONFLICT (page_id)
DO UPDATE SET
    page_name = EXCLUDED.page_name,
    access_token = EXCLUDED.access_token,
    ad_account_id = EXCLUDED.ad_account_id,
    updated_at = NOW();