#if UNITY_ANDROID
using System;
using UnityEngine;

namespace ILib.VoipMic
{

	public partial class VoipMicrophoneAndroid
	{
		class DataListener : AndroidJavaProxy
		{
			VoipMicrophoneAndroid m_Impl;

			public DataListener(VoipMicrophoneAndroid impl) : base("jp.ilib.mic.VoipMicrophone$DataListener")
			{
				m_Impl = impl;
			}

			public override AndroidJavaObject Invoke(string methodName, AndroidJavaObject[] javaArgs)
			{
				try
				{
					if (methodName == "OnRead")
					{
						var data = AndroidJNIHelper.ConvertFromJNIArray<float[]>(javaArgs[0].GetRawObject());
						var size = javaArgs[1].Call<int>("intValue");
						m_Impl.OnReadRaw?.Invoke(data, size);
						if (m_Impl.OnRead != null)
						{
							if (size == data.Length)
							{
								m_Impl.OnRead?.Invoke(data);
							}
							else
							{
								var buf = new float[size];
								Buffer.BlockCopy(data, 0, buf, 0, size);
								m_Impl.OnRead?.Invoke(buf);
							}
						}
					}
				}
				finally
				{
					foreach (var arg in javaArgs)
					{
						arg.Dispose();
					}
				}
				return null;
			}
		}
	}
}
#endif