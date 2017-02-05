drop table if EXISTS PEOPLE ;

create TABLE  PEOPLE (
    id SERIAL  PRIMARY KEY ,
    first_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    age INT NOT NULL

);