/**
create table bitly (
	shorturl text primary key,
	longurl text not null
);
**/
create table bitly (
	shorturl varchar(128) primary key,
	longurl varchar(128) not null,
	hash INTEGER not null
);

