CREATE TABLE users (
  uuid UUID PRIMARY KEY,
  username VARCHAR CONSTRAINT unq_username UNIQUE NOT NULL,
  password VARCHAR NOT NULL,
  email VARCHAR CONSTRAINT unq_email UNIQUE NOT NULL,
  bio VARCHAR NULL,
  image VARCHAR NULL
);

CREATE TABLE followers (
  follower_id UUID NOT NULL,
  followed_id UUID NOT NULL,
  CONSTRAINT follower_id_fkey FOREIGN KEY (follower_id)
    REFERENCES users (uuid) MATCH SIMPLE
    ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT followed_id_fkey FOREIGN KEY (followed_id)
    REFERENCES users (uuid) MATCH SIMPLE
    ON UPDATE NO ACTION ON DELETE NO ACTION,
  UNIQUE (follower_id, followed_id)
);

CREATE TABLE articles (
  uuid UUID PRIMARY KEY,
  slug VARCHAR CONSTRAINT unq_slug UNIQUE NOT NULL,
  title VARCHAR NOT NULL,
  description VARCHAR NOT NULL,
  body VARCHAR NOT NULL,
  author_id UUID NOT NULL,
  created_at timestamptz(3) NOT NULL,
  updated_at timestamptz(3) NOT NULL,
  CONSTRAINT author_id_fkey FOREIGN KEY (author_id)
    REFERENCES users (uuid) MATCH SIMPLE
    ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE TABLE article_tags (
  article_id UUID NOT NULL,
  tag varchar NOT NULL,
  CONSTRAINT article_id_fkey FOREIGN KEY (article_id)
    REFERENCES articles (uuid) MATCH SIMPLE
    ON UPDATE NO ACTION ON DELETE NO ACTION,
  UNIQUE (article_id, tag)
);

CREATE TABLE favorites (
  article_id UUID NOT NULL,
  user_id UUID NOT NULL,
  CONSTRAINT article_id_fkey FOREIGN KEY (article_id)
    REFERENCES articles (uuid) MATCH SIMPLE
    ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT user_id_fkey FOREIGN KEY (user_id)
    REFERENCES users (uuid) MATCH SIMPLE
    ON UPDATE NO ACTION ON DELETE NO ACTION,
  UNIQUE (article_id, user_id)
);
