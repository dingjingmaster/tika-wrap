.PHONY:all tika tika_wrap clean
all = tika tika_wrap

all:$(all)
	@echo 'done!'

clean:
	@echo 'cleaning ...'
	@mvn clean
	@echo 'clean done!'

tika:
	@echo 'build tika ...'
	cd $(shell pwd)/tika/ && mvn package -DskipTests

tika_wrap:
	@echo 'build tika-wrap ...'

