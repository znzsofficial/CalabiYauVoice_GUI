import { mount } from 'svelte';
import VideoToolsApp from './VideoToolsApp.svelte';
import './video.css';

const target = document.getElementById('video-app');

if (!target) {
  throw new Error('Missing #video-app mount target');
}

mount(VideoToolsApp, { target });
