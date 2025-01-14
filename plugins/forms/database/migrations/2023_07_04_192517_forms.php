<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('forms', function (Blueprint $table) {
            $table->id();
            $table->string('name');
            $table->string('slug')->unique();
            $table->string('icon')->nullable();
            $table->string('webhook')->nullable();
            $table->text('description')->nullable();
            $table->timestamps();
        });

        Schema::create('form_entries', function (Blueprint $table) {
            $table->id();
            $table->foreignId('form_id')->constrained()->onDelete('cascade');
            $table->json('data');
            $table->timestamps();
        });
    }
    public function down(): void
    {
        Schema::dropIfExists('forms');
        Schema::dropIfExists('form_entries');
    }
};
