#if UNITY_ANDROID
using System;
using UnityEngine;

namespace ILib.VoipMic
{

	public partial class VoipMicrophoneAndroid : IDisposable
	{
		public class Config
		{
			public bool UseBluetooth { get; set; } = false;
			public bool UseAcousticEchoCanceler { get; set; } = true;
			public bool UseAutomaticGainControl { get; set; } = true;
			public bool UseNoiseSuppressor { get; set; } = true;
		}

		public static VoipMicrophoneAndroid Start() => Start(new Config());

		public static VoipMicrophoneAndroid Start(Config config)
		{
			return new VoipMicrophoneAndroid(config);
		}

		AndroidJavaObject m_Impl;
		DataListener m_Listener;

		public Action<float[]> OnRead;
		public Action<float[], int> OnReadRaw;

		public bool AcousticEchoCancelerEnabled
		{
			get => m_Impl.Call<bool>("getAcousticEchoCancelerEnabled");
			set => m_Impl.Call<bool>("setAcousticEchoCancelerEnabled", value);
		}

		public bool AutomaticGainControlEnabled
		{
			get => m_Impl.Call<bool>("getAutomaticGainControlEnabled");
			set => m_Impl.Call<bool>("setAutomaticGainControlEnabled", value);
		}

		public bool NoiseSuppressorEnabled
		{
			get => m_Impl.Call<bool>("getNoiseSuppressorEnabled");
			set => m_Impl.Call<bool>("setNoiseSuppressorEnabled", value);
		}

		private VoipMicrophoneAndroid(Config config)
		{
			m_Impl = new AndroidJavaObject("jp.ilib.mic.VoipMicrophone");
			m_Impl.Set(nameof(config.UseBluetooth), config.UseBluetooth);
			m_Impl.Set(nameof(config.UseAcousticEchoCanceler), config.UseAcousticEchoCanceler);
			m_Impl.Set(nameof(config.UseAutomaticGainControl), config.UseAutomaticGainControl);
			m_Impl.Set(nameof(config.UseNoiseSuppressor), config.UseNoiseSuppressor);
			m_Listener = new DataListener(this);
			using (var player = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
			using (var context = player.GetStatic<AndroidJavaObject>("currentActivity"))
			{
				m_Impl.Call("start", context, m_Listener);
			}
		}

		public void Dispose()
		{
			m_Impl.Call("close");
			m_Impl = null;
		}
	}
}
#endif