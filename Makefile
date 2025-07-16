.PHONY:all tika tika_wrap demo clean
all = demo tika tika_wrap

all:$(all)
	@echo 'done!'

clean:
	@echo 'cleaning ...'
	@mvn clean
	@rm -rf $(shell pwd)/tmp-dir
	@echo 'clean done!'

tika:
	@echo 'build tika ...'
	#cd $(shell pwd)/tika/ && mvn package -DskipTests 
	cd $(shell pwd)/tika/ && mvn package -Ddetail=true -DskipTests -am 
	cp $(shell pwd)/tika/target/tika-main-1.0.0.jar $(shell pwd)/tmp-dir/

tika_wrap:
	@echo 'build tika-wrap ...'
	cmake -B build 
	make -C build
	cp $(shell pwd)/build/example/tika-wrap-demo $(shell pwd)/tmp-dir/
	cp $(shell pwd)/build/libtika-wrap.so $(shell pwd)/tmp-dir/

demo:
	mkdir -p $(shell pwd)/tmp-dir/

