//! Android USB Transport.
use std::time::Duration;
use anyhow::Result;
use jni::{JNIEnv, JavaVM};
use jni::objects::{GlobalRef, JClass, JObject};
use jni::sys::{jint, jlong};
use super::Transport;

pub struct AndroidTransport {
    vm: JavaVM,
    activity: GlobalRef,
}

impl AndroidTransport {
    pub fn new(vm: JavaVM, activity: GlobalRef) -> Result<Self> {
        Ok(Self { vm, activity })
    }
}

impl Transport for AndroidTransport {
    fn send_raw(&mut self, raw: &[u8]) -> Result<()> {
        let mut env = self.vm.attach_current_thread()?;
        let raw_java = env.byte_array_from_slice(raw)?;

        let result = env.call_method(self.activity.as_obj(), "sendRaw", "([B)I", &[raw_java.into()]);

        if let Err(e) = result {
            return Err(anyhow::anyhow!("sendRaw failed: {}", e));
        }

        Ok(())
    }

    fn recv_raw(&mut self, _timeout: Duration) -> Result<Vec<u8>> {
        let mut env = self.vm.attach_current_thread()?;
        let size = 64; // Max packet size

        let result = env.call_method(self.activity.as_obj(), "recvRaw", "(I)[B", &[size.into()]);

        if let Err(e) = result {
            return Err(anyhow::anyhow!("recvRaw failed: {}", e));
        }

        let result_obj = result.unwrap().l()?;

        let received_bytes = env.convert_byte_array(result_obj.into_inner())?;

        Ok(received_bytes)
    }
}
