#if UNITY_IOS
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;

namespace ILib.VoipMic
{
	public static class AudioSession
	{
		static Dictionary<Category, string> s_CategoryName = new Dictionary<Category, string>{
		{ Category.Ambient , "AVAudioSessionCategoryAmbient"},
		{ Category.MultiRoute , "AVAudioSessionCategoryMultiRoute"},
		{ Category.PlayAndRecord , "AVAudioSessionCategoryPlayAndRecord"},
		{ Category.Playback , "AVAudioSessionCategoryPlayback"},
		{ Category.Record , "AVAudioSessionCategoryRecord"},
		{ Category.SoloAmbient , "AVAudioSessionCategorySoloAmbient"},
	};

		public enum Category
		{
			Ambient,
			MultiRoute,
			PlayAndRecord,
			Playback,
			Record,
			SoloAmbient,
		}

		[Flags]
		public enum CategoryOptions
		{
			MixWithOthers = 0x1,
			DuckOthers = 0x2,
			InterruptSpokenAudioAndMixWithOthers = 0x11,
			AllowBluetooth = 0x4,
			AllowBluetoothA2DP = 0x20,
			AllowAirPlay = 0x40,
			DefaultToSpeaker = 0x8,
		}

		[DllImport("__Internal")]
		private static extern string ilib_voip_get_category();

		[DllImport("__Internal")]
		private static extern int ilib_voip_get_categoryoptions();

		[DllImport("__Internal")]
		private static extern void lib_voip_set_category(string category, int options);

		public static Category GetCategory()
		{
			var str = ilib_voip_get_category();
			foreach (var kvp in s_CategoryName)
			{
				if (kvp.Value == str)
				{
					return kvp.Key;
				}
			}
			return Category.Ambient;
		}

		public static CategoryOptions GetOptions()
		{
			return (CategoryOptions)ilib_voip_get_categoryoptions();
		}

		public static void SetCategory(Category category, CategoryOptions options)
		{
			lib_voip_set_category(s_CategoryName[category], (int)options);
		}

	}

}
#endif