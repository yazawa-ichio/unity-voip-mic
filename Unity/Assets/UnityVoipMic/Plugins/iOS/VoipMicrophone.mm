#include <AudioToolbox/AudioToolbox.h>
#include <AVFoundation/AVFoundation.h>

#ifdef __cplusplus
extern "C" {
#endif
typedef void (*VoipMicrophoneCallback)(int, float*, int);
#ifdef __cplusplus
}
#endif

static const AudioUnitElement kInputBus = 1;
static const AudioUnitElement kOutputBus = 0;
static const int kSampleRate = 48000;
static const int kChannel = 1;

struct InputData {
    int instanceId;
    VoipMicrophoneCallback callback;
    AudioUnit audioUnit;
    AudioBufferList* buffer;
};

OSStatus onRecorded(void* inRefCon, AudioUnitRenderActionFlags* ioActionFlags, const AudioTimeStamp* inTimeStamp, UInt32 inBusNumber, UInt32 inNumberFrames, AudioBufferList* ioData){
    InputData* data = ((InputData*)inRefCon);
    
    if (!data->buffer->mBuffers->mData) {
        data->buffer->mNumberBuffers = 1;
        AudioBuffer *b = data->buffer->mBuffers;
        b->mNumberChannels = kChannel;
        b->mDataByteSize = sizeof(float) * inNumberFrames;
        b->mData = calloc(1, b->mDataByteSize);
    }
    
    if (data->buffer->mBuffers->mDataByteSize < (sizeof(float) * inNumberFrames)) {
        AudioBuffer *b = data->buffer->mBuffers;
        if(!b->mData) {
            free(b->mData);
        }
        b->mDataByteSize = sizeof(float) * inNumberFrames;
        b->mData = calloc(1, b->mDataByteSize);
    }
    
    OSStatus err = AudioUnitRender(data->audioUnit, ioActionFlags, inTimeStamp, inBusNumber, inNumberFrames, data->buffer);
    if(err != noErr) {
        return err;
    }
    
    AudioBuffer buf = data->buffer->mBuffers[0];
    data->callback(data->instanceId, (float*)buf.mData, (int)buf.mDataByteSize/sizeof(float));
    
    return noErr;
}

OSStatus onRender(void* inRefCon, AudioUnitRenderActionFlags* ioActionFlags, const AudioTimeStamp* inTimeStamp, UInt32 inBusNumber, UInt32 inNumberFrames, AudioBufferList* ioData){
    return noErr;
}

@interface VoipMicrophone : NSObject {
@public
    InputData data;
}
- (void) start;
- (void) stop;
@end

@implementation VoipMicrophone
- (void) start{
    [self initAudioUnit];
    [self enableIO];
    [self setStreamFormat];
    [self enableAGC];
    [self setCallback];
    int initalize_attempts = 0;
    OSStatus result = AudioUnitInitialize(data.audioUnit);
    while (result != noErr) {
        ++initalize_attempts;
        if (initalize_attempts == 5) {
            AudioComponentInstanceDispose(data.audioUnit);
            return;
        }
        [NSThread sleepForTimeInterval:0.1f];
        result = AudioUnitInitialize(data.audioUnit);
    }
    AudioOutputUnitStart(data.audioUnit);
}
- (void) stop {
    if(data.buffer) {
        for(int i = 0; i < data.buffer->mNumberBuffers; i++) {
            if (data.buffer->mBuffers[i].mData)
            {
                free(data.buffer->mBuffers[i].mData);
            }
        }
        free(data.buffer);
    }
    AudioOutputUnitStop(data.audioUnit);
    AudioComponentInstanceDispose(data.audioUnit);
}
- (void) checkStatus: (OSStatus)status {
    if (status != noErr){
        AudioComponentInstanceDispose(data.audioUnit);
        [NSException raise:@"Invalid OSStatus status" format:@"status %d", (int)status];
    }
}
- (void) initAudioUnit{
    AudioComponentDescription desc;
    desc.componentType = kAudioUnitType_Output;
    desc.componentSubType = kAudioUnitSubType_VoiceProcessingIO;
    desc.componentManufacturer = kAudioUnitManufacturer_Apple;
    desc.componentFlags = 0;
    desc.componentFlagsMask = 0;
    AudioComponent ac = AudioComponentFindNext(NULL, &desc);
    
    OSStatus result = noErr;
    result = AudioComponentInstanceNew(ac, &data.audioUnit);
    [self checkStatus: result];
}
- (void)enableIO{
    OSStatus result = noErr;
    UInt32 enable = 1;
    result = AudioUnitSetProperty(data.audioUnit, kAudioOutputUnitProperty_EnableIO, kAudioUnitScope_Input, kInputBus, &enable, sizeof(enable));
    [self checkStatus: result];
    result = AudioUnitSetProperty(data.audioUnit, kAudioOutputUnitProperty_EnableIO, kAudioUnitScope_Output, kOutputBus, &enable, sizeof(enable));
    [self checkStatus: result];
}
- (void)setStreamFormat{
    OSStatus result = noErr;
    AudioStreamBasicDescription format = {0};
    format.mSampleRate = kSampleRate;
    format.mFormatID = kAudioFormatLinearPCM;
    format.mFormatFlags = kAudioFormatFlagsNativeFloatPacked;
    format.mBytesPerPacket = sizeof(float);
    format.mFramesPerPacket = 1;
    format.mBytesPerFrame =sizeof(float);
    format.mChannelsPerFrame = kChannel;
    format.mBitsPerChannel = 8 * sizeof(float);
    format.mReserved = 0;
    
    result = AudioUnitSetProperty(data.audioUnit, kAudioUnitProperty_StreamFormat, kAudioUnitScope_Output, kInputBus, &format, sizeof(format));
    [self checkStatus: result];
    result = AudioUnitSetProperty(data.audioUnit, kAudioUnitProperty_StreamFormat, kAudioUnitScope_Input, kOutputBus, &format, sizeof(format));
    [self checkStatus: result];
}
- (void) setCallback{
    OSStatus result = noErr;
    
    UInt32 flag = 0;
    result = AudioUnitSetProperty(data.audioUnit, kAudioUnitProperty_ShouldAllocateBuffer, kAudioUnitScope_Output, kInputBus, &flag, sizeof(flag));
    [self checkStatus: result];
    
    AURenderCallbackStruct outCallback;
    outCallback.inputProc = onRender;
    outCallback.inputProcRefCon = &data;
    result = AudioUnitSetProperty(data.audioUnit, kAudioUnitProperty_SetRenderCallback, kAudioUnitScope_Input, kOutputBus, &outCallback, sizeof(outCallback));
    [self checkStatus: result];
    
    AURenderCallbackStruct inCallback;
    inCallback.inputProc = onRecorded;
    inCallback.inputProcRefCon = &data;
    result = AudioUnitSetProperty(data.audioUnit, kAudioOutputUnitProperty_SetInputCallback, kAudioUnitScope_Global, kInputBus, &inCallback, sizeof(inCallback));
    [self checkStatus: result];
}
- (void) enableAGC{
    UInt32 agcEnabled = 0;
    OSStatus result = [self getAGC:&agcEnabled];
    if(result != noErr){
        return;
    }
    UInt32 enable_agc = 1;
    AudioUnitSetProperty(data.audioUnit,kAUVoiceIOProperty_VoiceProcessingEnableAGC,kAudioUnitScope_Global, kInputBus, &enable_agc,sizeof(enable_agc));
}
- (OSStatus) getAGC:(UInt32*)enabled{
    UInt32 size = sizeof(*enabled);
    OSStatus result = AudioUnitGetProperty(data.audioUnit,kAUVoiceIOProperty_VoiceProcessingEnableAGC,kAudioUnitScope_Global,kInputBus,enabled,&size);
    return  result;
}
@end



#ifdef __cplusplus
extern "C" {
#endif
VoipMicrophone* ilib_voip_mic_init(int instanceId, VoipMicrophoneCallback callback) {
    VoipMicrophone *sample = [[VoipMicrophone alloc] init];
    CFRetain((CFTypeRef)sample);
    sample->data.buffer = (AudioBufferList *) malloc (sizeof (AudioBufferList));
    sample->data.instanceId = instanceId;
    sample->data.callback = callback;
    [sample start];
    return sample;
}

void ilib_voip_mic_release(VoipMicrophone *obj) {
    [obj stop];
    CFRelease((CFTypeRef)obj);
}

char* ilib_voip_get_category() {
    AVAudioSession *audioSession = [AVAudioSession sharedInstance];
    char* category = (char *)[audioSession.category UTF8String];
    char* res = (char*)malloc(strlen(category) + 1);
    strcpy(res, category);
    return res;
}

int ilib_voip_get_categoryoptions() {
    AVAudioSession *audioSession = [AVAudioSession sharedInstance];
    return (int)audioSession.categoryOptions;
}

void lib_voip_set_category(char* category, int options) {
    [AVAudioSession.sharedInstance
     setCategory:(AVAudioSessionCategory)[NSString stringWithUTF8String:category]
     withOptions:(AVAudioSessionCategoryOptions)options
     error:nil];
}
#ifdef __cplusplus
}
#endif
