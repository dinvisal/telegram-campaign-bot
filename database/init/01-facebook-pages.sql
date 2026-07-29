CREATE TABLE IF NOT EXISTS facebook_pages (
    page_id VARCHAR(50) PRIMARY KEY,
    page_name VARCHAR(255) NOT NULL,
    access_token TEXT NOT NULL,
    ad_account_id VARCHAR(50)
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
);