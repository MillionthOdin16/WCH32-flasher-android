//! WCH ISP Protocol implementation.

mod android_logger;
pub mod constants;
pub mod device;
pub mod flashing;
pub mod format;
pub mod protocol;
pub mod transport;

pub use self::device::Chip;
pub use self::flashing::Flashing;
pub use self::protocol::{Command, Response};
pub use self::transport::{AndroidTransport, Baudrate, Transport};

use crate::android_logger::AndroidLogger;
use crate::android_logger::AndroidLogger;
use jni::objects::{JClass, JObject, JString};
use jni::sys::{jbyteArray, jstring};
use jni::JNIEnv;
use log::LevelFilter;

fn flash_internal(env: &JNIEnv, activity: JObject, firmware: jbyteArray) -> Result<(), anyhow::Error> {
    let firmware_bytes = env.convert_byte_array(firmware)?;

    let vm = env.get_java_vm()?;
    let activity_ref = env.new_global_ref(activity)?;
    let logger = AndroidLogger::new(vm.clone(), activity_ref.clone());

    log::set_boxed_logger(Box::new(logger))
        .map(|()| log::set_max_level(LevelFilter::Info))?;

    let transport = AndroidTransport::new(vm, activity_ref)?;

    let mut flasher = Flashing::new_from_transport(transport)?;

    flasher.flash(&firmware_bytes)?;

    Ok(())
}

#[no_mangle]
pub extern "system" fn Java_com_ch32flasher_MainActivity_nativeFlash(
    mut env: JNIEnv,
    _class: JClass,
    activity: JObject,
    firmware: jbyteArray,
) -> jstring {
    let result = flash_internal(&mut env, activity, firmware);

    if let Err(e) = result {
        let error_message = env.new_string(format!("Flashing failed: {}", e)).unwrap();
        return error_message.into_raw();
    }

    JObject::null().into_raw()
}
