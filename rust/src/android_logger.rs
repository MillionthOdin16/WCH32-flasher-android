use jni::{JNIEnv, JavaVM};
use jni::objects::{GlobalRef, JObject};
use log::{Level, Log, Metadata, Record};

pub struct AndroidLogger {
    vm: JavaVM,
    activity: GlobalRef,
}

impl AndroidLogger {
    pub fn new(vm: JavaVM, activity: GlobalRef) -> Self {
        Self { vm, activity }
    }
}

impl Log for AndroidLogger {
    fn enabled(&self, metadata: &Metadata) -> bool {
        metadata.level() <= Level::Info
    }

    fn log(&self, record: &Record) {
        if self.enabled(record.metadata()) {
            let mut env = self.vm.attach_current_thread().unwrap();
            let message = env.new_string(format!("{}", record.args())).unwrap();
            env.call_method(
                self.activity.as_obj(),
                "logMessage",
                "(Ljava/lang/String;)V",
                &[message.into()],
            )
            .unwrap();
        }
    }

    fn flush(&self) {}
}
