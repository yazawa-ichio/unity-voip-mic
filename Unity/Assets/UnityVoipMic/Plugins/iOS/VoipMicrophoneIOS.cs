#if UNITY_IOS
using AOT;
using System;
using System.Collections.Concurrent;
using System.Runtime.InteropServices;

namespace ILib.VoipMic
{

	public class VoipMicrophoneIOS : IDisposable
	{
		public class Config
		{
			public bool ChangeAudioSession { get; set; } = true;
			public AudioSession.CategoryOptions CategoryOptions { get; set; } = AudioSession.CategoryOptions.DefaultToSpeaker | AudioSession.CategoryOptions.AllowBluetoothA2DP;
		}

		delegate void MicrophoneCallback(int val, IntPtr data, int size);

		static ConcurrentDictionary<int, VoipMicrophoneIOS> s_Dic = new ConcurrentDictionary<int, VoipMicrophoneIOS>();
		static int s_Counter;

		public static VoipMicrophoneIOS Start() => Start(new Config());

		public static VoipMicrophoneIOS Start(Config config)
		{
			return new VoipMicrophoneIOS(++s_Counter, config);
		}

		[DllImport("__Internal")]
		static extern IntPtr ilib_voip_mic_init(int instanceId, MicrophoneCallback callback);

		[DllImport("__Internal")]
		static extern void ilib_voip_mic_release(IntPtr handle);

		[MonoPInvokeCallback(typeof(MicrophoneCallback))]
		static void Callback(int instanceId, IntPtr data, int size)
		{
			if (s_Dic.TryGetValue(instanceId, out var mic))
			{
				mic.Read(data, size);
			}
		}

		Config m_Config;
		int m_Id;
		IntPtr m_Handle;
		GCHandle m_Callback;
		float[] m_Buffer = new float[1024];
		AudioSession.Category m_Category;
		AudioSession.CategoryOptions m_Options;

		public Action<float[]> OnRead;
		public Action<float[], int> OnReadRaw;

		private VoipMicrophoneIOS(int id, Config config)
		{
			m_Config = config;
			if (config.ChangeAudioSession)
			{
				m_Category = AudioSession.GetCategory();
				m_Options = AudioSession.GetOptions();
				AudioSession.SetCategory(AudioSession.Category.PlayAndRecord, config.CategoryOptions);
			}
			MicrophoneCallback callback = Callback;
			m_Callback = GCHandle.Alloc(callback);
			m_Id = id;
			s_Dic[id] = this;
			m_Handle = ilib_voip_mic_init(m_Id, callback);
		}

		~VoipMicrophoneIOS()
		{
			Dispose();
		}

		public void Dispose()
		{
			if (m_Handle == IntPtr.Zero) return;
			try
			{
				ilib_voip_mic_release(m_Handle);
				m_Handle = IntPtr.Zero;
			}
			finally
			{
				m_Callback.Free();
				if (m_Config.ChangeAudioSession)
				{
					AudioSession.SetCategory(m_Category, m_Options);
				}
			}
			GC.SuppressFinalize(this);
		}

		void Read(IntPtr data, int size)
		{
			if (m_Buffer.Length < size)
			{
				Array.Resize(ref m_Buffer, size);
			}
			Marshal.Copy(data, m_Buffer, 0, size);
			OnReadRaw?.Invoke(m_Buffer, size);
			if (OnRead != null)
			{
				var buf = new float[size];
				Buffer.BlockCopy(m_Buffer, 0, buf, 0, size * sizeof(float));
				OnRead?.Invoke(buf);
			}
		}

	}

}
#endif