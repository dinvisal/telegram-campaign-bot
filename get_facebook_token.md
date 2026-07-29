1. Get token for exchange long-live token
https://graph.facebook.com/v19.0/oauth/access_token?grant_type=fb_exchange_token&client_id=1036434462501249&client_secret=ecd6b7de2cd2643687a5e754a05170fc&fb_exchange_token={$short_live_token}
2. Get long-live token 
https://graph.facebook.com/v19.0/me/accounts?access_token={$return_short_live_token}
3. Insert page to database
INSERT INTO facebook_pages (page_id, page_name, access_token, ad_account_id)
VALUES ('635100196572436', 'Cambodia Online Shopping', '{$token}', '1218704455475267');

https://graph.facebook.com/v19.0/act_3447385832186137/campaigns?fields=id,name,status,objective,daily_budget,lifetime_budget,start_time,stop_time,insights%7Bspend,impressions,clicks%7D&filtering=%5B%7B%22field%22:%22effective_status%22,%22operator%22:%22IN%22,%22value%22:%5B%22ACTIVE%22,%22PAUSED%22,%22ARCHIVED%22%5D%7D%5D&limit=100&access_token={$token}