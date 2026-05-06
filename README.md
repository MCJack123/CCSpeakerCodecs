# CCSpeakerCodecs
Adds a collection of alternative transit codecs for CC: Tweaked speakers to enhance audio quality.

## Theory
CC: Tweaked speakers internally use the 1 bit per sample DFPWM codec in transit to reduce bandwidth while keeping a high audio sample rate. It works fairly well for audio mostly below ~2000 Hz with little high frequency components, but it starts to break down when the high-frequency components are important - for example, in a waveform with sharp edges (which create harmonics), such as square/sawtooth waves.

Since DFPWM has a pretty hard frequency shelf at 12 kHz, I figured that reducing the sample rate wouldn't have as much of an impact and would leave room to use a better codec like ADPCM. So that's what this mod does - it lets the user switch to higher bit depth codecs at the expense of sample rate.

All codecs are limited to up to the same 48 kbps rate as DFPWM, with the rate scaled down to fit. The speaker still takes in the same 48 kHz sample rate, but samples are skipped to hit the sample rate target - high-performance applications can ignore the skipped samples and set them to 0.

Over the network, the audio packets are made backwards compatible with clients and servers without this mod installed. Extended codec packets are encoded as empty DFPWM packets followed by the actual data, which makes unaware clients decode an empty packet when an unsupported codec is used. DFPWM codec packets remain the same format, so unaware clients still receive a valid packet & unaware servers are still sending a supported packet.

## Codecs
| Codec    | b/sample | Sample rate | Speed | Quality |
|----------|----------|-------------|-------|---------|
| `dfpwm`  | 1        | 48000       | ⭐⭐⭐⭐⭐ | ⭐       |
| `qoa`    | 3.2      | 12000       | ⭐⭐⭐⭐⭐ | ⭐⭐      |
| `adpcm2` | 2        | 24000       | ⭐⭐⭐   | ⭐⭐⭐     |
| `adpcm3` | 3        | 16000       | ⭐⭐⭐   | ⭐⭐⭐     |
| `adpcm`  | 4        | 12000       | ⭐⭐⭐   | ⭐⭐      |
| `adpcm5` | 5        | 9600        | ⭐⭐    | ⭐⭐      |
| `opus`   | *        | 48000       | ⭐⭐⭐⭐  | ⭐⭐⭐⭐⭐   |

| Codec    | [chipneve.xm](https://modarchive.org/index.php?request=view_by_moduleid&query=36799) (chiptune, no interpolation) | Yahtzee.xm (low sample rate GBA module, linear interpolation)                                 | [Stereo Madness 2](https://www.newgrounds.com/audio/listen/590577) (WAV, 48 kHz 16-bit)   |
|----------|-------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| `dfpwm`  | https://raw.githubusercontent.com/MCJack123/CCSpeakerCodecs/master/demos/DFPWM%20chipneve.wav                     | https://raw.githubusercontent.com/MCJack123/CCSpeakerCodecs/master/demos/DFPWM%20yahtzee.wav  | https://raw.githubusercontent.com/MCJack123/CCSpeakerCodecs/master/demos/DFPWM%20sm2.wav  |
| `qoa`    | https://raw.githubusercontent.com/MCJack123/CCSpeakerCodecs/master/demos/QOA%20chipneve.wav                       | https://raw.githubusercontent.com/MCJack123/CCSpeakerCodecs/master/demos/QOA%20yahtzee.wav    | https://raw.githubusercontent.com/MCJack123/CCSpeakerCodecs/master/demos/QOA%20sm2.wav    |
| `adpcm2` | https://raw.githubusercontent.com/MCJack123/CCSpeakerCodecs/master/demos/ADPCM2%20chipneve.wav                    | https://raw.githubusercontent.com/MCJack123/CCSpeakerCodecs/master/demos/ADPCM2%20yahtzee.wav | https://raw.githubusercontent.com/MCJack123/CCSpeakerCodecs/master/demos/ADPCM2%20sm2.wav |
| `adpcm3` | https://raw.githubusercontent.com/MCJack123/CCSpeakerCodecs/master/demos/ADPCM3%20chipneve.wav                    | https://raw.githubusercontent.com/MCJack123/CCSpeakerCodecs/master/demos/ADPCM3%20yahtzee.wav | https://raw.githubusercontent.com/MCJack123/CCSpeakerCodecs/master/demos/ADPCM3%20sm2.wav |
| `adpcm`  | https://raw.githubusercontent.com/MCJack123/CCSpeakerCodecs/master/demos/ADPCM4%20chipneve.wav                    | https://raw.githubusercontent.com/MCJack123/CCSpeakerCodecs/master/demos/ADPCM4%20yahtzee.wav | https://raw.githubusercontent.com/MCJack123/CCSpeakerCodecs/master/demos/ADPCM4%20sm2.wav |
| `adpcm5` | https://raw.githubusercontent.com/MCJack123/CCSpeakerCodecs/master/demos/ADPCM5%20chipneve.wav                    | https://raw.githubusercontent.com/MCJack123/CCSpeakerCodecs/master/demos/ADPCM5%20yahtzee.wav | https://raw.githubusercontent.com/MCJack123/CCSpeakerCodecs/master/demos/ADPCM5%20sm2.wav |

### Codec options
Some codecs accept additional options in a table passed as the second parameter to `setAudioCodec`. These are the following options available:

#### `interpolation: boolean`
**Available in:** `qoa`, `adpcm2`, `adpcm3`, `adpcm`, `adpcm5`

Sets whether the audio is interpolated (linearly) on the client side. Defaults to true.

Interpolation smoothens out the audio and makes it less crunchy, but the crunch may be desired for some audio, so the option is made available just in case.

#### `bufferSize: number`
**Available in:** `opus`

Controls the size of the audio buffer/minimum frame size in samples. May be any of 120, 240, 480, 960, or 1920. Defaults to 960.

Lower values will reduce the audio delay from buffering, but can cause clicks due to the smaller buffers having lower quality encoding. Notably, values of 120 and 240 are significantly worse than the higher values.

## Usage
Simply call the new `speaker.setAudioCodec(codec: string[, options: table])` method to set the codec. Future `playAudio` calls will use this codec automatically, with no format changes necessary.

## License
The core mod is licensed under MPL 2.0, like CC:T. Portions are taken from third-party projects licensed under other terms - see LICENSE.txt for more info.
