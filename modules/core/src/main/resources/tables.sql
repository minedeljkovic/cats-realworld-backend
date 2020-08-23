CREATE TABLE users (
  uuid UUID PRIMARY KEY,
  username VARCHAR CONSTRAINT unq_username UNIQUE NOT NULL,
  password VARCHAR NOT NULL,
  email VARCHAR CONSTRAINT unq_email UNIQUE NOT NULL,
  bio VARCHAR NULL,
  image VARCHAR NULL
);

CREATE TABLE followers (
  follower_id UUID,
  followed_id UUID,
  CONSTRAINT follower_id_fkey FOREIGN KEY (follower_id)
    REFERENCES users (uuid) MATCH SIMPLE
    ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT followed_id_fkey FOREIGN KEY (followed_id)
    REFERENCES users (uuid) MATCH SIMPLE
    ON UPDATE NO ACTION ON DELETE NO ACTION,
  UNIQUE (follower_id, followed_id)
);
