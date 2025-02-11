.PHONY:all tika tika_wrap demo clean
all = tika tika_wrap demo

all:$(all)
	@echo 'done!'

clean:
	@echo 'cleaning ...'
	@mvn clean
	@rm -rf $(shell pwd)/tmp-dir
	@echo 'clean done!'

tika:
	@echo 'build tika ...'
	cd $(shell pwd)/tika/ && mvn package -DskipTests -pl tika-main -am

tika_wrap:
	@echo 'build tika-wrap ...'
	cd $(shell pwd)/src/ && cmake -B build 
	cd $(shell pwd)/src/ && make -C build

demo:
	mkdir -p $(shell pwd)/tmp-dir/
	cp $(shell pwd)/tika/tika-main/target/tika-main-1.0.0.jar $(shell pwd)/tmp-dir/
	cp $(shell pwd)/src/build/tika-wrap-demo $(shell pwd)/tmp-dir/
	cp $(shell pwd)/src/build/libtika-wrap.so $(shell pwd)/tmp-dir/

