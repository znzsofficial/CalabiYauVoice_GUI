import { mount } from 'svelte';
import NativeVideoApp from './NativeVideoApp.svelte';
import './native-video.css';

const target = document.getElementById('video-app');

if (!target) {
  throw new Error('Missing #video-app mount target');
}

mount(NativeVideoApp, { target });
