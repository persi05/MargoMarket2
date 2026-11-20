ALTER TABLE auction_reports ADD COLUMN bid_id BIGINT;
ALTER TABLE auction_reports ADD CONSTRAINT auction_report_bid FOREIGN KEY(auction_id,bid_id) REFERENCES auction_bids(auction_id,id);
ALTER TABLE auction_reports DROP CONSTRAINT auction_reports_auction_id_user_id_key;
CREATE UNIQUE INDEX auction_report_once ON auction_reports(auction_id,user_id) WHERE bid_id IS NULL;
CREATE UNIQUE INDEX auction_bid_report_once ON auction_reports(bid_id,user_id) WHERE bid_id IS NOT NULL;
CREATE INDEX auction_reports_queue ON auction_reports(created_at DESC,id DESC) WHERE resolved_at IS NULL;
